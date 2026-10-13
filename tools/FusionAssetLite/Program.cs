using System.Buffers;
using System.Buffers.Binary;
using System.Diagnostics;
using System.Drawing;
using System.Globalization;
using System.IO.Compression;
using System.IO.Hashing;
using System.Runtime.CompilerServices;
using System.Runtime.ExceptionServices;
using System.Runtime.InteropServices;
using System.Runtime.Intrinsics;
using System.Runtime.Intrinsics.X86;
using System.Text;
using System.Threading;
using System.Threading.Channels;
using K4os.Compression.LZ4;

namespace FusionAssetLite;

internal static class Program
{
    private static int Main(string[] args)
    {
        static bool HasArg(string[] values, string value) =>
            values.Any(arg => string.Equals(arg, value, StringComparison.OrdinalIgnoreCase));

        if (args.Length == 0 || HasArg(args, "--help") || HasArg(args, "-h"))
        {
            Console.WriteLine("Usage: FusionAssetLite <game.exe|game.ccn> [output-root] [--no-images] [--no-sounds] [--no-pack] [--no-shaders] [--zip]");
            return args.Length == 0 ? 1 : 0;
        }

        string inputPath = Path.GetFullPath(args[0]);
        if (!File.Exists(inputPath))
        {
            Console.Error.WriteLine("Input file not found: " + inputPath);
            return 1;
        }

        string? outputArg = args.Skip(1).FirstOrDefault(arg => !arg.StartsWith("-", StringComparison.Ordinal));
        string inputDir = Path.GetDirectoryName(inputPath) ?? Environment.CurrentDirectory;
        string outputRoot = outputArg != null
            ? Path.GetFullPath(outputArg)
            : Path.Combine(inputDir, "extracted_assets_lite");

        DumperOptions options = new()
        {
            DumpImages = !HasArg(args, "--no-images"),
            DumpSounds = !HasArg(args, "--no-sounds"),
            DumpPackData = !HasArg(args, "--no-pack"),
            DumpShaders = !HasArg(args, "--no-shaders"),
            ZipOutput = HasArg(args, "--zip")
        };

        try
        {
            FusionAssetDumper dumper = new(inputPath, outputRoot, options);
            dumper.Run();
            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            return 1;
        }
    }
}

internal sealed class DumperOptions
{
    public bool DumpImages { get; init; } = true;
    public bool DumpSounds { get; init; } = true;
    public bool DumpPackData { get; init; } = true;
    public bool DumpShaders { get; init; } = true;
    public bool ZipOutput { get; init; }
}

internal sealed class FusionAssetDumper
{
    private const uint MagicPame = 0x454D4150; // "PAME"
    private const uint MagicPamu = 0x554D4150; // "PAMU"
    private const uint MagicPackData = 0x77777777;
    private const ushort LegacyLastChunk = 0x7F7F;
    private const ushort LegacyAltChunk = 0x222C;
    private const ushort ChunkAppName = 0x2224;
    private const ushort ChunkShaders = 0x2243;
    private const ushort ChunkExtendedHeader = 0x2245;
    private const ushort ChunkShadersAlt = 0x225A;
    private const ushort ChunkPlus = 0x2253;
    private const ushort ChunkImageBank = 0x6666;
    private const ushort ChunkSoundBank = 0x6668;
    private const ushort ChunkSeeded = 0x7EEE;
    private const ushort ChunkLast = 0x7F7F;
    private const int MaxPackFiles = 100_000;
    private const int MaxImages = 1_000_000;
    private const int MaxSounds = 100_000;
    private const int MaxShaders = 10_000;
    private const int MaxNameLength = 4096;
    private const int MaxShaderSourceBytes = 16 * 1024 * 1024;

    private readonly string _inputPath;
    private readonly string _outputRoot;
    private readonly DumperOptions _options;
    private readonly Stopwatch _timer = new();

    private string _appName;
    private string _dumpDir = string.Empty;
    private bool? _unicode;
    private float _fusion = 2.5f;
    private int _build;
    private bool _windows;
    private bool _flash;
    private bool _android;
    private bool _ios;
    private bool _html;
    private bool _plus;
    private bool _seeded;
    private bool _premultipliedAlpha;
    private bool _optimizeImageSize;

    private int _imagesWritten;
    private int _imageFailures;
    private int _soundsWritten;
    private int _soundFailures;
    private int _packFilesWritten;
    private int _packFailures;
    private int _shaderFilesWritten;
    private int _shaderFailures;
    private readonly int[] _imageModes = new int[256];
    private readonly object _pathLock = new();
    private readonly HashSet<string> _usedPaths = new(StringComparer.OrdinalIgnoreCase);
    private readonly object _zipInitLock = new();
    private ZipArchive? _zipArchive;
    private FileStream? _zipStream;
    private string? _zipPath;
    private Channel<ZipWriteJob>? _zipWriteQueue;
    private Task? _zipWriterTask;
    private ExceptionDispatchInfo? _zipWriterFailure;

    public FusionAssetDumper(string inputPath, string outputRoot, DumperOptions options)
    {
        _inputPath = inputPath;
        _outputRoot = outputRoot;
        _options = options;
        _appName = Path.GetFileNameWithoutExtension(inputPath);
        _dumpDir = Path.Combine(_outputRoot, Sanitizer.FileName(_appName));
    }

    public void Run()
    {
        _timer.Start();
        if (_options.ZipOutput)
            Directory.CreateDirectory(_outputRoot);
        else
            Directory.CreateDirectory(_dumpDir);

        try
        {
            using FileStream fs = new(_inputPath, FileMode.Open, FileAccess.Read, FileShare.Read, 1 << 20, FileOptions.SequentialScan);
            using BinaryReader reader = new(fs, Encoding.UTF8, leaveOpen: true);

            if (LooksLikeExe(reader))
            {
                long entryPoint = CalculateEntryPoint(reader);
                fs.Position = entryPoint;
                Console.WriteLine($"Input: normal EXE, payload at 0x{entryPoint:X}");
            }
            else
            {
                fs.Position = 0;
                Console.WriteLine("Input: raw CCN/data stream");
            }

            ReadPackDataIfPresent(reader);
            SeekToPackageHeaderIfNeeded(reader);
            ReadPackage(reader);
            CompleteZipOutput();
        }
        finally
        {
            DisposeZipOutput();
        }

        Console.WriteLine();
        Console.WriteLine("Done.");
        Console.WriteLine($"Output: {GetOutputPath()}");
        Console.WriteLine($"Images: {_imagesWritten} written, {_imageFailures} failed");
        Console.WriteLine($"Sounds: {_soundsWritten} written, {_soundFailures} failed");
        Console.WriteLine($"Packed data: {_packFilesWritten} written, {_packFailures} failed");
        Console.WriteLine($"Shader files: {_shaderFilesWritten} written, {_shaderFailures} failed");
        string[] imageModes = _imageModes
            .Select((count, mode) => new { count, mode })
            .Where(x => x.count > 0)
            .Select(x => $"{x.mode}={x.count}")
            .ToArray();
        if (imageModes.Length > 0)
            Console.WriteLine("Image modes: " + string.Join(", ", imageModes));
        Console.WriteLine($"Elapsed: {_timer.Elapsed.TotalSeconds:F3}s");
        Console.WriteLine($"Peak RAM: {Process.GetCurrentProcess().PeakWorkingSet64 / 1024 / 1024} MB");
    }

    private static bool LooksLikeExe(BinaryReader reader)
    {
        reader.BaseStream.Position = 0;
        return reader.BaseStream.Length >= 2 && BinaryText.ReadAscii(reader, 2) == "MZ";
    }

    private static long CalculateEntryPoint(BinaryReader reader)
    {
        Stream stream = reader.BaseStream;
        stream.Position = 0;
        if (BinaryText.ReadAscii(reader, 2) != "MZ")
            return 0;

        if (stream.Length < 0x40)
            throw new InvalidDataException("EXE is too small to contain a PE header.");

        stream.Position = 0x3C;
        uint peHeader = reader.ReadUInt32();
        if (peHeader > stream.Length - 24)
            throw new InvalidDataException($"Invalid PE header offset 0x{peHeader:X}.");

        stream.Position = peHeader;
        if (BinaryText.ReadAscii(reader, 4) != "PE")
            throw new InvalidDataException($"Missing PE signature at 0x{peHeader:X}.");

        _ = reader.ReadUInt16(); // machine
        ushort sectionCount = reader.ReadUInt16();
        stream.Seek(12, SeekOrigin.Current);
        ushort optionalHeaderSize = reader.ReadUInt16();
        stream.Seek(2, SeekOrigin.Current);

        long sectionTable = peHeader + 4 + 20 + optionalHeaderSize;
        long sectionTableEnd = sectionTable + sectionCount * 40L;
        if (sectionTableEnd > stream.Length)
            throw new InvalidDataException("PE section table extends past end of file.");

        stream.Position = sectionTable;

        long payloadOffset = 0;
        for (int i = 0; i < sectionCount; i++)
        {
            stream.Seek(16, SeekOrigin.Current);
            uint sizeOfRawData = reader.ReadUInt32();
            uint pointerToRawData = reader.ReadUInt32();
            stream.Seek(16, SeekOrigin.Current);

            if (sizeOfRawData == 0)
                continue;

            long sectionEnd = pointerToRawData + (long)sizeOfRawData;
            if (sectionEnd <= stream.Length)
                payloadOffset = Math.Max(payloadOffset, sectionEnd);
        }

        if (payloadOffset <= 0 || payloadOffset >= stream.Length)
            throw new InvalidDataException("Could not locate a Fusion payload after PE sections.");

        return payloadOffset;
    }

    private void ReadPackDataIfPresent(BinaryReader reader)
    {
        if (!reader.HasBytes(4))
            return;

        uint marker = reader.PeekUInt32();
        if (marker is MagicPame or MagicPamu)
            return;

        if (marker == MagicPackData)
        {
            reader.BaseStream.Seek(28, SeekOrigin.Current);
        }
        else if ((marker & 0xFFFF) is LegacyLastChunk or LegacyAltChunk)
        {
            if (reader.MatchesBytesAt(4, 0x49, 0x87, 0x47, 0x12))
                reader.BaseStream.Seek(28, SeekOrigin.Current);
            else
            {
                _fusion = 1.5f;
                _unicode = false;
                Console.WriteLine("PackData: legacy format not implemented in lite mode, skipping.");
                return;
            }
        }
        else if ((ushort)(marker & 0xFFFF) == 1)
        {
            _fusion = 1.1f;
            _unicode = false;
            Console.WriteLine("PackData: legacy format not implemented in lite mode, skipping.");
            return;
        }
        else
        {
            Console.WriteLine("PackData: none detected");
            return;
        }

        uint count = reader.ReadUInt32();
        if (count > MaxPackFiles)
            throw new InvalidDataException($"PackData file count out of range: {count}");

        Console.WriteLine($"PackData: {count} files");
        if (count > 0)
            _unicode = DetectLengthPrefixedStringUnicode(reader);

        string packDir = Path.Combine(_dumpDir, "Packed Data");
        if (_options.DumpPackData)
            CreateOutputDirectory(packDir);

        for (uint i = 0; i < count; i++)
        {
            try
            {
                int nameLength = ValidateLength(reader.ReadInt16(), "PackData name");
                string name = ReadUniversal(reader, nameLength);
                _ = reader.ReadInt32();
                int dataSize = ValidateRemainingSize(reader, reader.ReadInt32(), "PackData file");

                if (!_options.DumpPackData)
                {
                    reader.SkipBytesExact(dataSize);
                    continue;
                }

                string outPath = UniquePath(Path.Combine(packDir, Sanitizer.RelativePath(name)));
                WritePackDataFile(reader, dataSize, outPath);
                _packFilesWritten++;
            }
            catch (Exception ex)
            {
                _packFailures++;
                Console.WriteLine($"PackData file {i:D5} failed: {ex.Message}");
                if (!reader.HasBytes(1))
                    break;
            }
        }
    }

    private static bool DetectLengthPrefixedStringUnicode(BinaryReader reader)
    {
        long pos = reader.BaseStream.Position;
        try
        {
            if (!reader.HasBytes(4))
                return false;

            short length = reader.ReadInt16();
            return length > 0 && reader.HasBytes(2) && reader.BaseStream.ReadByte() >= 0 && reader.BaseStream.ReadByte() == 0;
        }
        finally
        {
            reader.BaseStream.Position = pos;
        }
    }

    private void SeekToPackageHeaderIfNeeded(BinaryReader reader)
    {
        if (!reader.HasBytes(4))
            return;

        uint marker = reader.PeekUInt32();
        if (marker is MagicPame or MagicPamu)
            return;

        long start = reader.BaseStream.Position;
        long header = FindPackageHeader(reader.BaseStream, start);
        if (header < 0)
        {
            reader.BaseStream.Position = start;
            return;
        }

        reader.BaseStream.Position = header;
        Console.WriteLine($"Package: skipped {header - start:N0} bytes to header at 0x{header:X}");
    }

    private static long FindPackageHeader(Stream stream, long start)
    {
        const int BufferSize = 1 << 20;
        byte[] buffer = new byte[BufferSize + 3];
        int overlap = 0;
        stream.Position = start;

        while (stream.Position < stream.Length)
        {
            long readStart = stream.Position - overlap;
            int read = stream.Read(buffer, overlap, BufferSize);
            if (read == 0)
                break;

            int total = overlap + read;
            ReadOnlySpan<byte> searchable = buffer.AsSpan(0, total - 3);
            int searchStart = 0;
            while (searchStart < searchable.Length)
            {
                int relative = searchable[searchStart..].IndexOf((byte)'P');
                if (relative < 0)
                    break;

                int i = searchStart + relative;
                if (buffer[i] == (byte)'P' &&
                    buffer[i + 1] == (byte)'A' &&
                    buffer[i + 2] == (byte)'M' &&
                    (buffer[i + 3] == (byte)'E' || buffer[i + 3] == (byte)'U'))
                {
                    return readStart + i;
                }

                searchStart = i + 1;
            }

            overlap = Math.Min(3, total);
            Array.Copy(buffer, total - overlap, buffer, 0, overlap);
        }

        return -1;
    }

    private void ReadPackage(BinaryReader reader)
    {
        string header = BinaryText.ReadAscii(reader, 4);
        if (header is not ("PAME" or "PAMU"))
            throw new InvalidDataException($"Unsupported package header '{header}' at 0x{reader.BaseStream.Position - 4:X}.");

        short runtimeVersion = reader.ReadInt16();
        _ = reader.ReadInt16(); // runtime subversion
        int productVersion = reader.ReadInt32();
        _build = reader.ReadInt32();

        if (runtimeVersion == 769)
            _fusion = 1.5f;
        else if (_build < 280)
            _fusion = 2.0f + (productVersion == 1 ? 0.1f : 0);
        else
            _fusion = 2.5f;

        _unicode = header != "PAME";
        Console.WriteLine($"Package: {header}, Fusion {_fusion:0.0}, build {_build}");

        bool stopAfterChunk = false;
        while (reader.HasBytes(8) && !stopAfterChunk)
        {
            long chunkStart = reader.BaseStream.Position;
            short id = reader.ReadInt16();
            short flag = reader.ReadInt16();
            int size = reader.ReadInt32();
            long payloadStart = reader.BaseStream.Position;
            long payloadEnd = payloadStart + size;

            if (size < 0 || payloadEnd > reader.BaseStream.Length)
                throw new InvalidDataException($"Bad chunk size at 0x{chunkStart:X}: id=0x{(ushort)id:X4}, size={size}");

            try
            {
                switch ((ushort)id)
                {
                    case ChunkAppName:
                        ReadAppNameChunk(reader, size, flag);
                        break;
                    case ChunkShaders when _options.DumpShaders:
                        ReadShaderBankChunk(reader, size, flag, "Shaders");
                        break;
                    case ChunkExtendedHeader:
                        ReadExtendedHeaderChunk(reader, size, flag);
                        break;
                    case ChunkShadersAlt when _options.DumpShaders:
                        ReadShaderBankChunk(reader, size, flag, "Shaders");
                        break;
                    case ChunkPlus:
                        _plus = true;
                        break;
                    case ChunkImageBank when _options.DumpImages:
                        DumpImageBank(reader, size, flag);
                        break;
                    case ChunkSoundBank when _options.DumpSounds:
                        DumpSoundBank(reader, size, flag);
                        break;
                    case ChunkSeeded:
                        _seeded = true;
                        break;
                    case ChunkLast:
                        stopAfterChunk = true;
                        break;
                }
            }
            finally
            {
                reader.BaseStream.Position = payloadEnd;
            }
        }
    }

    private void ReadAppNameChunk(BinaryReader reader, int size, short flag)
    {
        using BinaryReader chunk = OpenChunkReader(reader, size, flag);
        string appName = ReadUniversal(chunk);
        if (string.IsNullOrWhiteSpace(appName))
            return;

        _appName = appName.TrimEnd('\0');
        string desiredDumpDir = Path.Combine(_outputRoot, Sanitizer.FileName(_appName));
        if (_options.ZipOutput)
        {
            if (_zipArchive == null)
                _dumpDir = desiredDumpDir;
            Console.WriteLine("App: " + _appName);
            return;
        }

        if (!Path.GetFullPath(desiredDumpDir).Equals(Path.GetFullPath(_dumpDir), StringComparison.OrdinalIgnoreCase) &&
            !Directory.EnumerateFileSystemEntries(_dumpDir).Any())
        {
            Directory.Delete(_dumpDir);
            _dumpDir = desiredDumpDir;
            Directory.CreateDirectory(_dumpDir);
        }

        Console.WriteLine("App: " + _appName);
    }

    private void ReadExtendedHeaderChunk(BinaryReader reader, int size, short flag)
    {
        using BinaryReader chunk = OpenChunkReader(reader, size, flag);
        uint flags = chunk.ReadUInt32();
        byte buildType = chunk.ReadByte();
        _windows = buildType is 0 or 1 or 2;
        _flash = buildType == 10;
        _android = buildType is 12 or 20 or 34;
        _ios = buildType is 13 or 14 or 15;
        _html = buildType is 27 or 28;
        chunk.BaseStream.Seek(3, SeekOrigin.Current);
        uint compressionFlags = chunk.ReadUInt32();

        _premultipliedAlpha = BitFlag.IsSet(flags, 29);
        _optimizeImageSize = BitFlag.IsSet(compressionFlags, 9);

        Console.WriteLine($"Extended header: buildType={buildType}, optimizeImageSize={_optimizeImageSize}");
    }

    private void DumpImageBank(BinaryReader reader, int size, short flag)
    {
        Console.WriteLine($"ImageBank: {(flag == 0 ? "streaming" : "streamed compressed chunk")}, {size:N0} bytes");
        using BinaryReader bank = OpenSequentialChunkReader(reader, size, flag);
        int imageCount = ValidateCount((_android || _ios || _flash || _html) ? ReadMobileCount(bank) : bank.ReadInt32(), "Image", MaxImages);
        string imageDir = Path.Combine(_dumpDir, "Images");
        CreateOutputDirectory(imageDir);

        ProgressMeter progress = new("Images", imageCount);
        int workerCount = Math.Max(1, Environment.ProcessorCount);
        int queueCapacity = Math.Clamp(workerCount * 16, 128, 512);
        Channel<ImageExportJob> exportQueue = Channel.CreateBounded<ImageExportJob>(new BoundedChannelOptions(queueCapacity)
        {
            SingleWriter = true,
            SingleReader = false,
            FullMode = BoundedChannelFullMode.Wait
        });
        Task[] workers = Enumerable.Range(0, workerCount)
            .Select(_ => Task.Run(() => ExportImages(exportQueue.Reader, imageDir)))
            .ToArray();

        try
        {
            for (int i = 0; i < imageCount; i++)
            {
                ImageExportJob job;
                try
                {
                    job = ReadImageJob(bank);
                    try
                    {
                        WriteImageJob(exportQueue.Writer, job);
                    }
                    catch
                    {
                        job.Clear();
                        throw;
                    }
                }
                catch (Exception ex)
                {
                    Interlocked.Increment(ref _imageFailures);
                    Console.WriteLine($"Image {i:D5} parse failed; aborting image bank to avoid stream desynchronization: {ex.Message}");
                    progress.Step(i + 1);
                    break;
                }

                progress.Step(i + 1);
            }
        }
        finally
        {
            exportQueue.Writer.TryComplete();
            try
            {
                Task.WaitAll(workers);
            }
            catch (AggregateException ex)
            {
                throw ex.Flatten();
            }
        }

        progress.Done();
    }

    private static void WriteImageJob(ChannelWriter<ImageExportJob> writer, ImageExportJob job)
    {
        while (true)
        {
            if (writer.TryWrite(job))
                return;

            if (!writer.WaitToWriteAsync().AsTask().GetAwaiter().GetResult())
                throw new InvalidOperationException("Image export queue was closed before the job was accepted.");
        }
    }

    private void ExportImages(ChannelReader<ImageExportJob> images, string imageDir)
    {
        while (images.WaitToReadAsync().AsTask().GetAwaiter().GetResult())
        {
            while (images.TryRead(out ImageExportJob? job))
            {
                LiteImage? image = null;
                try
                {
                    image = DecodeImage(job);
                    Interlocked.Increment(ref _imageModes[image.GraphicMode]);

                    string outPath = UniquePath(Path.Combine(imageDir, $"{image.Handle:D5}.png"));
                    SavePng(image, outPath);
                    Interlocked.Increment(ref _imagesWritten);
                }
                catch (Exception ex)
                {
                    Interlocked.Increment(ref _imageFailures);
                    Console.WriteLine($"Image {job.Handle:D5} export failed: {ex.Message}");
                }
                finally
                {
                    image?.Clear();
                    job.Clear();
                }
            }
        }
    }

    private void WritePackDataFile(BinaryReader reader, int dataSize, string outPath)
    {
        long payloadStart = reader.BaseStream.Position;
        try
        {
            using Stream output = OpenOutputWriteStream(outPath);
            using WindowedReadStream payload = new(reader.BaseStream, payloadStart, dataSize);
            if (IsNextPayloadZlib(reader, dataSize))
                Compression.DecompressTo(payload, output);
            else
                payload.CopyTo(output);

            CompleteOutputWriteStream(output);
        }
        finally
        {
            reader.BaseStream.Position = payloadStart + dataSize;
        }
    }

    private static bool IsNextPayloadZlib(BinaryReader reader, int dataSize)
    {
        if (dataSize < 2)
            return false;

        long position = reader.BaseStream.Position;
        int first = reader.BaseStream.ReadByte();
        int second = reader.BaseStream.ReadByte();
        reader.BaseStream.Position = position;
        return first >= 0 && second >= 0 && Compression.IsZlibHeader((byte)first, (byte)second);
    }

    private static int ReadMobileCount(BinaryReader reader)
    {
        reader.BaseStream.Seek(2, SeekOrigin.Current);
        return reader.ReadInt16();
    }

    private ImageExportJob ReadImageJob(BinaryReader reader)
    {
        return _optimizeImageSize && _fusion > 2.0f && !_flash && !_android && !_ios && !_html
            ? ReadImage25PlusJob(reader)
            : ReadImage25Job(reader);
    }

    private ImageExportJob ReadImage25Job(BinaryReader reader)
    {
        uint rawHandle = reader.ReadUInt32();
        uint handle = NormalizeHandle(rawHandle);
        int decompressedSize = ValidateNonNegative(reader.ReadInt32(), "Image decompressed size");
        int compressedSize = ValidateRemainingSize(reader, reader.ReadInt32(), "Image compressed data");
        return new ImageExportJob
        {
            Kind = ImagePayloadKind.Standard,
            Handle = handle,
            DecompressedSize = decompressedSize,
            Payload = reader.ReadRentedBytesExact(compressedSize)
        };
    }

    private LiteImage DecodeImage(ImageExportJob job)
    {
        if (job.Kind == ImagePayloadKind.Optimized25Plus)
            return DecodeImage25Plus(job);

        RentedBuffer payload = job.Payload ?? throw new InvalidDataException($"Image {job.Handle:D5} has no compressed payload.");
        RentedBuffer decompressed = Compression.DecompressExact(payload.Buffer, 0, payload.Length, job.DecompressedSize);
        return ParseImage25Payload(decompressed, job.Handle);
    }

    private static LiteImage ParseImage25Payload(RentedBuffer decompressed, uint handle)
    {
        const int HeaderSize = 32;
        bool attachedDecompressed = false;
        ReadOnlySpan<byte> payload = decompressed.Buffer.AsSpan(0, decompressed.Length);
        if (payload.Length < HeaderSize)
            throw new InvalidDataException($"Image payload is too small: {payload.Length} bytes.");

        LiteImage image = new()
        {
            Handle = handle,
            Checksum = BinaryPrimitives.ReadInt32LittleEndian(payload[0..4]),
            References = BinaryPrimitives.ReadInt32LittleEndian(payload[4..8])
        };

        try
        {
            int dataSize = ValidateNonNegative(BinaryPrimitives.ReadInt32LittleEndian(payload[8..12]), "Image data");
            image.Width = BinaryPrimitives.ReadInt16LittleEndian(payload[12..14]);
            image.Height = BinaryPrimitives.ReadInt16LittleEndian(payload[14..16]);
            ValidateImageDimensions(image.Width, image.Height);
            image.GraphicMode = payload[16];
            image.Flags = payload[17];
            image.HotspotX = BinaryPrimitives.ReadInt16LittleEndian(payload[20..22]);
            image.HotspotY = BinaryPrimitives.ReadInt16LittleEndian(payload[22..24]);
            image.ActionPointX = BinaryPrimitives.ReadInt16LittleEndian(payload[24..26]);
            image.ActionPointY = BinaryPrimitives.ReadInt16LittleEndian(payload[26..28]);
            image.TransparentColor = Color.FromArgb(payload[31], payload[28], payload[29], payload[30]);

            if (image.Flag(ImageFlags.Lzx))
            {
                int compressedDataOffset = HeaderSize + 4;
                if (compressedDataOffset > payload.Length)
                    throw new InvalidDataException($"LZX image data is truncated: {payload.Length} bytes.");

                byte[] lzxData = Compression.DecompressBlock(decompressed.Buffer, compressedDataOffset, payload.Length - compressedDataOffset);
                image.SetImageData(lzxData, 0, lzxData.Length);
                decompressed.Dispose();
            }
            else
            {
                if (dataSize > payload.Length - HeaderSize)
                    throw new InvalidDataException($"Image data is truncated: needed {dataSize} bytes, got {payload.Length - HeaderSize}.");

                image.SetImageData(decompressed, HeaderSize, dataSize);
                attachedDecompressed = true;
            }

            return image;
        }
        catch
        {
            image.Clear();
            if (!attachedDecompressed)
                decompressed.Dispose();
            throw;
        }
    }

    private ImageExportJob ReadImage25PlusJob(BinaryReader reader)
    {
        ImageExportJob job = new()
        {
            Kind = ImagePayloadKind.Optimized25Plus,
            Handle = NormalizeHandle(reader.ReadUInt32()),
            Checksum = reader.ReadInt32(),
            References = reader.ReadInt32()
        };
        reader.BaseStream.Seek(4, SeekOrigin.Current);
        int dataSize = ValidateRemainingSize(reader, reader.ReadInt32(), "Image data");
        job.Width = reader.ReadInt16();
        job.Height = reader.ReadInt16();
        ValidateImageDimensions(job.Width, job.Height);
        job.GraphicMode = reader.ReadByte();
        job.Flags = reader.ReadByte();
        reader.BaseStream.Seek(2, SeekOrigin.Current);
        job.HotspotX = reader.ReadInt16();
        job.HotspotY = reader.ReadInt16();
        job.ActionPointX = reader.ReadInt16();
        job.ActionPointY = reader.ReadInt16();
        job.TransparentColor = BinaryText.ReadColor(reader);

        job.DecompressedSize = ValidateImageBufferSize(reader.ReadInt32(), job.Width, job.Height);
        if (dataSize < 4)
            throw new InvalidDataException($"Optimized image data size out of range: {dataSize}");

        job.Payload = reader.ReadRentedBytesExact(dataSize - 4);
        return job;
    }

    private static LiteImage DecodeImage25Plus(ImageExportJob job)
    {
        RentedBuffer payload = job.Payload ?? throw new InvalidDataException($"Image {job.Handle:D5} has no compressed payload.");
        RentedBuffer imageData = RentedBuffer.Rent(job.DecompressedSize);
        bool attachedImageData = false;
        LiteImage image = new()
        {
            Handle = job.Handle,
            Checksum = job.Checksum,
            References = job.References,
            Width = job.Width,
            Height = job.Height,
            GraphicMode = job.GraphicMode,
            Flags = job.Flags,
            HotspotX = job.HotspotX,
            HotspotY = job.HotspotY,
            ActionPointX = job.ActionPointX,
            ActionPointY = job.ActionPointY,
            TransparentColor = job.TransparentColor
        };
        try
        {
            int decoded = LZ4Codec.Decode(payload.Buffer, 0, payload.Length, imageData.Buffer, 0, imageData.Length);
            if (decoded != job.DecompressedSize)
                throw new InvalidDataException($"LZ4 image decoded {decoded} bytes, expected {job.DecompressedSize}.");

            image.SetImageData(imageData, 0, imageData.Length);
            attachedImageData = true;
            return image;
        }
        catch
        {
            image.Clear();
            if (!attachedImageData)
                imageData.Dispose();
            throw;
        }
    }

    private void SavePng(LiteImage image, string path)
    {
        TranslatorContext context = new()
        {
            Build = _build,
            Fusion = _fusion,
            Plus = _plus,
            Seeded = _seeded,
            PremultipliedAlpha = _premultipliedAlpha
        };

        if (_options.ZipOutput)
        {
            RentedBuffer? png = FastPngWriter.EncodeImage(image, context);
            try
            {
                WriteOutputBuffer(path, png);
                png = null;
            }
            finally
            {
                png?.Dispose();
            }
        }
        else
        {
            FastPngWriter.WriteImage(path, image, context);
        }
    }

    private void DumpSoundBank(BinaryReader reader, int size, short flag)
    {
        Console.WriteLine($"SoundBank: {(flag == 0 ? "streaming" : "streamed compressed chunk")}, {size:N0} bytes");
        using BinaryReader bank = OpenSequentialChunkReader(reader, size, flag);
        int count = ValidateCount((_android || _ios || _flash || _html) ? ReadMobileCount(bank) : bank.ReadInt32(), "Sound", MaxSounds);
        if (_android || _ios || _flash || _html)
        {
            Console.WriteLine("SoundBank: mobile/Flash/HTML sound banks are not implemented in lite mode, skipping.");
            return;
        }

        string soundDir = Path.Combine(_dumpDir, "Sounds");
        CreateOutputDirectory(soundDir);

        ProgressMeter progress = new("Sounds", count);
        int workerCount = Math.Max(1, Math.Min(Environment.ProcessorCount, count));
        Channel<SoundExportJob> exportQueue = Channel.CreateBounded<SoundExportJob>(new BoundedChannelOptions(Math.Clamp(workerCount * 4, 16, 128))
        {
            SingleWriter = true,
            SingleReader = false,
            FullMode = BoundedChannelFullMode.Wait
        });
        Task[] workers = Enumerable.Range(0, workerCount)
            .Select(_ => Task.Run(() => ExportSounds(exportQueue.Reader, soundDir)))
            .ToArray();

        try
        {
            for (int i = 0; i < count; i++)
            {
                SoundExportJob job;
                try
                {
                    job = ReadSoundJob(bank);
                    try
                    {
                        WriteSoundJob(exportQueue.Writer, job);
                    }
                    catch
                    {
                        job.Clear();
                        throw;
                    }
                }
                catch (Exception ex)
                {
                    Interlocked.Increment(ref _soundFailures);
                    Console.WriteLine($"Sound {i:D5} parse failed; aborting sound bank to avoid stream desynchronization: {ex.Message}");
                    progress.Step(i + 1);
                    break;
                }

                progress.Step(i + 1);
            }
        }
        finally
        {
            exportQueue.Writer.TryComplete();
            try
            {
                Task.WaitAll(workers);
            }
            catch (AggregateException ex)
            {
                throw ex.Flatten();
            }
        }

        progress.Done();
    }

    private static void WriteSoundJob(ChannelWriter<SoundExportJob> writer, SoundExportJob job)
    {
        while (true)
        {
            if (writer.TryWrite(job))
                return;

            if (!writer.WaitToWriteAsync().AsTask().GetAwaiter().GetResult())
                throw new InvalidOperationException("Sound export queue was closed before the job was accepted.");
        }
    }

    private void ExportSounds(ChannelReader<SoundExportJob> sounds, string soundDir)
    {
        while (sounds.WaitToReadAsync().AsTask().GetAwaiter().GetResult())
        {
            while (sounds.TryRead(out SoundExportJob? job))
            {
                try
                {
                    SoundAsset sound = DecodeSound(job);
                    string safeName = string.IsNullOrWhiteSpace(sound.Name) ? $"sound_{sound.Handle:D5}" : Sanitizer.FileName(sound.Name);
                    string ext = sound.GetExtension();
                    string outPath = UniquePath(Path.Combine(soundDir, $"{safeName}.{ext}"));
                    WriteOutputBytes(outPath, sound.Data, sound.DataOffset, sound.DataLength);
                    Interlocked.Increment(ref _soundsWritten);
                }
                catch (Exception ex)
                {
                    Interlocked.Increment(ref _soundFailures);
                    Console.WriteLine($"Sound {job.Handle:D5} export failed: {ex.Message}");
                }
                finally
                {
                    job.Clear();
                }
            }
        }
    }

    private SoundExportJob ReadSoundJob(BinaryReader reader)
    {
        uint rawHandle = reader.ReadUInt32();
        SoundExportJob job = new()
        {
            Handle = NormalizeHandle(rawHandle),
            Checksum = reader.ReadInt32(),
            References = reader.ReadUInt32()
        };
        int decompressedSize = ValidateNonNegative(reader.ReadInt32(), "Sound decompressed size");
        job.Flags = reader.ReadUInt32();
        job.Frequency = reader.ReadInt32();
        job.NameLength = ValidateLength(reader.ReadInt32(), "Sound name");
        job.DecompressedSize = decompressedSize;

        job.PlayFromDisk = BitFlag.IsSet(job.Flags, 5);
        if (!job.PlayFromDisk)
        {
            int compressedSize = ValidateRemainingSize(reader, reader.ReadInt32(), "Sound compressed data");
            job.Payload = reader.ReadBytesExact(compressedSize);
        }
        else
        {
            job.Payload = reader.ReadBytesExact(ValidateRemainingSize(reader, decompressedSize, "Sound data"));
        }

        return job;
    }

    private SoundAsset DecodeSound(SoundExportJob job)
    {
        byte[] payload = job.PlayFromDisk
            ? job.Payload
            : Compression.DecompressExact(job.Payload, job.DecompressedSize);
        SoundAsset sound = new()
        {
            Handle = job.Handle,
            Checksum = job.Checksum,
            References = job.References,
            Flags = job.Flags,
            Frequency = job.Frequency
        };
        using MemoryStream ms = new(payload, writable: false);
        using BinaryReader soundReader = new(ms);
        sound.Name = ReadUniversalStop(soundReader, job.NameLength);
        sound.Data = payload;
        sound.DataOffset = checked((int)soundReader.BaseStream.Position);
        sound.DataLength = checked(payload.Length - sound.DataOffset);
        return sound;
    }

    private void ReadShaderBankChunk(BinaryReader reader, int size, short flag, string folderName)
    {
        using BinaryReader chunk = OpenChunkReader(reader, size, flag);
        if (!chunk.HasBytes(4))
            return;

        int count = chunk.ReadInt32();
        if (count < 0 || count > MaxShaders)
            return;

        int[] offsets = new int[count];
        for (int i = 0; i < count; i++)
            offsets[i] = chunk.ReadInt32();

        string shaderDir = Path.Combine(_dumpDir, folderName);
        CreateOutputDirectory(shaderDir);

        List<ShaderAsset> shaders = new();
        for (int i = 0; i < count; i++)
        {
            if (offsets[i] == 0)
                continue;

            try
            {
                if (offsets[i] < 0 || offsets[i] >= chunk.BaseStream.Length)
                    throw new InvalidDataException($"Shader offset out of range: {offsets[i]}");

                chunk.BaseStream.Position = offsets[i];
                ShaderAsset shader = ReadShader(chunk, i);
                if (shader.FxData.Length == 0)
                    continue;

                shaders.Add(shader);
            }
            catch (Exception ex)
            {
                _shaderFailures++;
                Console.WriteLine($"Shader {i:D3} failed: {ex.Message}");
            }
        }

        Parallel.ForEach(shaders, new ParallelOptions { MaxDegreeOfParallelism = Math.Max(1, Environment.ProcessorCount) }, shader =>
        {
            try
            {
                string baseName = Sanitizer.FileName(Path.GetFileNameWithoutExtension(shader.Name));
                if (string.IsNullOrWhiteSpace(baseName))
                    baseName = $"shader_{shader.Handle:D3}";

                (string fxPath, string xmlPath) = UniqueShaderPaths(shaderDir, baseName, shader.Compiled ? ".fxc" : ".fx");
                WriteOutputBytes(fxPath, shader.FxData);
                Interlocked.Increment(ref _shaderFilesWritten);

                WriteOutputText(xmlPath, shader.ToXml());
                Interlocked.Increment(ref _shaderFilesWritten);
            }
            catch (Exception ex)
            {
                Interlocked.Increment(ref _shaderFailures);
                Console.WriteLine($"Shader {shader.Handle:D3} failed: {ex.Message}");
            }
        });
    }

    private ShaderAsset ReadShader(BinaryReader reader, int index)
    {
        long start = reader.BaseStream.Position;
        int nameOffset = reader.ReadInt32();
        int fxDataOffset = reader.ReadInt32();
        int parameterOffset = reader.ReadInt32();
        _ = reader.ReadInt32(); // options offset
        int fxDataSize = ValidateNonNegative(reader.ReadInt32(), "Shader data size");

        ShaderAsset shader = new() { Handle = index };
        if (_build >= 296 && Math.Abs(_fusion - 2.5f) < 0.01f)
        {
            shader.Name = $"Shader_{index}.fx";
        }
        else if (nameOffset != 0)
        {
            SeekRelativeOffset(reader, start, nameOffset, "Shader name");
            shader.Name = BinaryText.ReadAsciiZ(reader, MaxNameLength);
        }

        if (fxDataOffset != 0)
        {
            SeekRelativeOffset(reader, start, fxDataOffset, "Shader data");
            string header = BinaryText.ReadAscii(reader, 4);
            shader.Compiled = header == "DXBC";
            reader.BaseStream.Seek(-4, SeekOrigin.Current);
            if (shader.Compiled)
            {
                int byteCount = ValidateRemainingSize(reader, Math.Max(0, fxDataSize - 1), "Compiled shader data");
                shader.FxData = reader.ReadBytesExact(byteCount);
            }
            else
            {
                shader.FxData = BinaryText.ReadBytesZ(reader, MaxShaderSourceBytes);
            }
        }

        if (parameterOffset != 0)
        {
            SeekRelativeOffset(reader, start, parameterOffset, "Shader parameters");
            int paramCount = reader.ReadInt32();
            if (paramCount is > 0 and < 1024)
            {
                int typeOffset = reader.ReadInt32();
                int nameOffset2 = reader.ReadInt32();
                byte[] types = new byte[paramCount];

                SeekRelativeOffset(reader, start + parameterOffset, typeOffset, "Shader parameter types");
                for (int i = 0; i < paramCount; i++)
                    types[i] = reader.ReadByte();

                SeekRelativeOffset(reader, start + parameterOffset, nameOffset2, "Shader parameter names");
                for (int i = 0; i < paramCount; i++)
                    shader.Parameters.Add(new ShaderParameter(types[i], BinaryText.ReadAsciiZ(reader, MaxNameLength)));
            }
        }

        return shader;
    }

    private uint NormalizeHandle(uint rawHandle) =>
        _build >= 284 && rawHandle > 0 ? rawHandle - 1 : rawHandle;

    private static int ValidateCount(int count, string label, int max)
    {
        if (count < 0 || count > max)
            throw new InvalidDataException($"{label} count out of range: {count}");

        return count;
    }

    private static int ValidateLength(int length, string label)
    {
        if (length < 0 || length > MaxNameLength)
            throw new InvalidDataException($"{label} length out of range: {length}");

        return length;
    }

    private static int ValidateNonNegative(int value, string label)
    {
        if (value < 0)
            throw new InvalidDataException($"{label} is negative: {value}");

        return value;
    }

    private static int ValidateRemainingSize(BinaryReader reader, int size, string label)
    {
        ValidateNonNegative(size, label);
        if (!reader.HasBytes(size))
            throw new EndOfStreamException($"{label} needs {size} bytes but only {reader.BaseStream.Length - reader.BaseStream.Position} remain.");

        return size;
    }

    private static void ValidateImageDimensions(short width, short height)
    {
        if (width <= 0 || height <= 0)
            throw new InvalidDataException($"Image dimensions out of range: {width}x{height}");

        long pixels = width * (long)height;
        if (pixels > 100_000_000)
            throw new InvalidDataException($"Image dimensions are too large: {width}x{height}");
    }

    private static int ValidateImageBufferSize(int size, short width, short height)
    {
        ValidateImageDimensions(width, height);
        ValidateNonNegative(size, "Image buffer size");

        long maxReasonable = width * (long)height * 8 + height * 8L + 4096;
        if (size > maxReasonable)
            throw new InvalidDataException($"Image buffer size {size} is too large for {width}x{height}.");

        return size;
    }

    private static void SeekRelativeOffset(BinaryReader reader, long start, int offset, string label)
    {
        if (offset < 0)
            throw new InvalidDataException($"{label} offset is negative: {offset}");

        long position = start + offset;
        if (position < 0 || position >= reader.BaseStream.Length)
            throw new InvalidDataException($"{label} offset out of range: {offset}");

        reader.BaseStream.Position = position;
    }

    private BinaryReader OpenChunkReader(BinaryReader reader, int size, short flag)
    {
        if (flag == 0)
        {
            WindowedReadStream window = new(reader.BaseStream, reader.BaseStream.Position, size);
            return new BinaryReader(new BufferedStream(window, 81920), Encoding.UTF8, leaveOpen: false);
        }

        byte[] payload = ReadChunkPayload(reader, size, flag);
        return new BinaryReader(new MemoryStream(payload, writable: false), Encoding.UTF8, leaveOpen: false);
    }

    private BinaryReader OpenSequentialChunkReader(BinaryReader reader, int size, short flag)
    {
        if (flag == 0)
        {
            WindowedReadStream window = new(reader.BaseStream, reader.BaseStream.Position, size);
            return new BinaryReader(new BufferedStream(window, 81920), Encoding.UTF8, leaveOpen: false);
        }

        if (flag == 1)
        {
            Stream stream = OpenCompressedChunkStream(reader, size);
            return new BinaryReader(stream, Encoding.UTF8, leaveOpen: false);
        }

        throw new NotSupportedException($"Chunk flag {flag} is not supported by the lite dumper.");
    }

    private static Stream OpenCompressedChunkStream(BinaryReader reader, int size)
    {
        if (size < 8)
            throw new InvalidDataException($"Compressed chunk is too small: {size} bytes.");

        int decompressedSize = ValidateNonNegative(reader.ReadInt32(), "Chunk decompressed size");
        int compressedSize = ValidateRemainingSize(reader, reader.ReadInt32(), "Compressed chunk data");
        if (compressedSize > size - 8)
            throw new InvalidDataException($"Compressed chunk data exceeds chunk size: {compressedSize} > {size - 8}");

        bool zlib = IsNextPayloadZlib(reader, compressedSize);
        WindowedReadStream window = new(reader.BaseStream, reader.BaseStream.Position, compressedSize);
        BufferedStream compressed = new(window, 81920);
        Stream decompressor = zlib
            ? new ZLibStream(compressed, CompressionMode.Decompress)
            : new DeflateStream(compressed, CompressionMode.Decompress);
        return new ExactLengthReadStream(decompressor, decompressedSize);
    }

    private static byte[] ReadChunkPayload(BinaryReader reader, int size, short flag)
    {
        if (flag == 0)
            return reader.ReadBytesExact(size);

        if (flag == 1)
        {
            if (size < 8)
                throw new InvalidDataException($"Compressed chunk is too small: {size} bytes.");

            long start = reader.BaseStream.Position;
            int decompressedSize = ValidateNonNegative(reader.ReadInt32(), "Chunk decompressed size");
            int compressedSize = ValidateRemainingSize(reader, reader.ReadInt32(), "Compressed chunk data");
            if (compressedSize > size - 8)
                throw new InvalidDataException($"Compressed chunk data exceeds chunk size: {compressedSize} > {size - 8}");

            byte[] compressed = reader.ReadBytesExact(compressedSize);
            reader.BaseStream.Position = start + size;
            return Compression.DecompressExact(compressed, decompressedSize);
        }

        throw new NotSupportedException($"Chunk flag {flag} is not supported by the lite dumper.");
    }

    private string GetOutputPath() =>
        _options.ZipOutput ? GetZipPath() : _dumpDir;

    private string GetZipPath() =>
        _zipPath ?? Path.ChangeExtension(_dumpDir, ".zip");

    private void CreateOutputDirectory(string path)
    {
        if (!_options.ZipOutput)
            Directory.CreateDirectory(path);
    }

    private Stream OpenOutputWriteStream(string path)
    {
        if (_options.ZipOutput)
            return new DeferredZipEntryStream(this, path);

        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        return new FileStream(path, FileMode.Create, FileAccess.Write, FileShare.None, 81920);
    }

    private static void CompleteOutputWriteStream(Stream stream)
    {
        if (stream is DeferredZipEntryStream deferred)
            deferred.Submit();
    }

    private void WriteOutputBytes(string path, ReadOnlySpan<byte> data)
    {
        if (_options.ZipOutput)
        {
            byte[] copy = data.ToArray();
            EnqueueZipWrite(ZipWriteJob.FromArray(ToZipEntryName(path), copy, 0, copy.Length));
            return;
        }

        WriteFileBytes(path, data);
    }

    private void WriteOutputBytes(string path, byte[] data) =>
        WriteOutputBytes(path, data, 0, data.Length);

    private void WriteOutputBytes(string path, byte[] data, int offset, int length)
    {
        if (offset < 0 || length < 0 || offset > data.Length - length)
            throw new InvalidDataException($"Output data range is outside the buffer: offset={offset}, length={length}, buffer={data.Length}.");

        if (_options.ZipOutput)
        {
            EnqueueZipWrite(ZipWriteJob.FromArray(ToZipEntryName(path), data, offset, length));
            return;
        }

        WriteFileBytes(path, data.AsSpan(offset, length));
    }

    private void WriteOutputBuffer(string path, RentedBuffer data)
    {
        if (_options.ZipOutput)
        {
            EnqueueZipWrite(ZipWriteJob.FromRentedBuffer(ToZipEntryName(path), data));
            return;
        }

        try
        {
            WriteFileBytes(path, data.Buffer.AsSpan(0, data.Length));
        }
        finally
        {
            data.Dispose();
        }
    }

    private static void WriteFileBytes(string path, ReadOnlySpan<byte> data)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        using var handle = File.OpenHandle(
            path,
            FileMode.Create,
            FileAccess.Write,
            FileShare.None,
            FileOptions.SequentialScan,
            preallocationSize: data.Length);
        RandomAccess.Write(handle, data, 0);
    }

    private void WriteOutputText(string path, string text) =>
        WriteOutputBytes(path, Encoding.UTF8.GetBytes(text));

    private void EnqueueZipWrite(ZipWriteJob job)
    {
        bool accepted = false;
        try
        {
            ChannelWriter<ZipWriteJob> writer = EnsureZipWriter();
            while (true)
            {
                ThrowIfZipWriterFailed();
                if (writer.TryWrite(job))
                {
                    accepted = true;
                    return;
                }

                if (!writer.WaitToWriteAsync().AsTask().GetAwaiter().GetResult())
                {
                    ThrowIfZipWriterFailed();
                    throw new InvalidOperationException("Zip writer stopped before accepting the output entry.");
                }
            }
        }
        catch (ChannelClosedException)
        {
            ThrowIfZipWriterFailed();
            throw;
        }
        finally
        {
            if (!accepted)
                job.Dispose();
        }
    }

    private ChannelWriter<ZipWriteJob> EnsureZipWriter()
    {
        Channel<ZipWriteJob>? queue = _zipWriteQueue;
        if (queue != null)
            return queue.Writer;

        lock (_zipInitLock)
        {
            queue = _zipWriteQueue;
            if (queue != null)
                return queue.Writer;

            _zipPath = GetZipPath();
            Directory.CreateDirectory(Path.GetDirectoryName(_zipPath)!);
            _zipStream = new FileStream(_zipPath, FileMode.Create, FileAccess.ReadWrite, FileShare.None, 1 << 20);
            _zipArchive = new ZipArchive(_zipStream, ZipArchiveMode.Create, leaveOpen: true);
            Volatile.Write(ref _zipWriterFailure, null);
            queue = Channel.CreateBounded<ZipWriteJob>(new BoundedChannelOptions(Math.Clamp(Environment.ProcessorCount * 2, 8, 64))
            {
                SingleReader = true,
                SingleWriter = false,
                FullMode = BoundedChannelFullMode.Wait
            });
            _zipWriteQueue = queue;
            _zipWriterTask = Task.Run(() => RunZipWriter(queue));
            return queue.Writer;
        }
    }

    private void RunZipWriter(Channel<ZipWriteJob> queue)
    {
        ChannelReader<ZipWriteJob> reader = queue.Reader;
        ZipArchive archive = _zipArchive ?? throw new InvalidOperationException("Zip archive was not initialized.");
        try
        {
            while (reader.WaitToReadAsync().AsTask().GetAwaiter().GetResult())
            {
                while (reader.TryRead(out ZipWriteJob? job))
                {
                    using (job)
                    {
                        ZipArchiveEntry entry = archive.CreateEntry(job.EntryName, CompressionLevel.NoCompression);
                        using Stream stream = entry.Open();
                        stream.Write(job.Data);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Volatile.Write(ref _zipWriterFailure, ExceptionDispatchInfo.Capture(ex));
            queue.Writer.TryComplete(ex);
            while (reader.TryRead(out ZipWriteJob? job))
                job.Dispose();
            throw;
        }
    }

    private void CompleteZipOutput()
    {
        Channel<ZipWriteJob>? queue = _zipWriteQueue;
        if (queue == null)
            return;

        queue.Writer.TryComplete();
        _zipWriterTask?.GetAwaiter().GetResult();
        DisposeZipOutput();
    }

    private void DisposeZipOutput()
    {
        Channel<ZipWriteJob>? queue = _zipWriteQueue;
        if (queue != null)
        {
            queue.Writer.TryComplete();
            try
            {
                _zipWriterTask?.GetAwaiter().GetResult();
            }
            catch
            {
                // Preserve the original failure while still releasing the archive handles.
            }
        }

        _zipArchive?.Dispose();
        _zipArchive = null;
        _zipStream?.Dispose();
        _zipStream = null;
        _zipWriteQueue = null;
        _zipWriterTask = null;
        Volatile.Write(ref _zipWriterFailure, null);
    }

    private void ThrowIfZipWriterFailed()
    {
        ExceptionDispatchInfo? failure = Volatile.Read(ref _zipWriterFailure);
        if (failure != null)
            failure.Throw();

        if (_zipWriterTask is { IsFaulted: true, Exception: not null } task)
        {
            failure = ExceptionDispatchInfo.Capture(task.Exception.GetBaseException());
            Volatile.Write(ref _zipWriterFailure, failure);
            failure.Throw();
        }
    }

    private string ToZipEntryName(string path)
    {
        string relative = Path.GetRelativePath(_dumpDir, Path.GetFullPath(path));
        if (relative.StartsWith("..", StringComparison.Ordinal) || Path.IsPathRooted(relative))
            relative = Path.GetFileName(path);

        return relative
            .Replace(Path.DirectorySeparatorChar, '/')
            .Replace(Path.AltDirectorySeparatorChar, '/');
    }

    private sealed class ZipWriteJob : IDisposable
    {
        private readonly RentedBuffer? _owner;

        private ZipWriteJob(string entryName, byte[] buffer, int offset, int length, RentedBuffer? owner)
        {
            EntryName = entryName;
            Buffer = buffer;
            Offset = offset;
            Length = length;
            _owner = owner;
        }

        public string EntryName { get; }
        public byte[] Buffer { get; }
        public int Offset { get; }
        public int Length { get; }
        public ReadOnlySpan<byte> Data => Buffer.AsSpan(Offset, Length);

        public static ZipWriteJob FromArray(string entryName, byte[] buffer, int offset, int length)
        {
            if (offset < 0 || length < 0 || offset > buffer.Length - length)
                throw new InvalidDataException($"Zip entry range is outside the buffer: offset={offset}, length={length}, buffer={buffer.Length}.");

            return new ZipWriteJob(entryName, buffer, offset, length, owner: null);
        }

        public static ZipWriteJob FromRentedBuffer(string entryName, RentedBuffer buffer) =>
            new(entryName, buffer.Buffer, 0, buffer.Length, buffer);

        public void Dispose() => _owner?.Dispose();
    }

    private sealed class DeferredZipEntryStream : MemoryStream
    {
        private readonly FusionAssetDumper _owner;
        private readonly string _path;
        private bool _submitted;

        public DeferredZipEntryStream(FusionAssetDumper owner, string path)
        {
            _owner = owner;
            _path = path;
        }

        public void Submit()
        {
            if (_submitted)
                return;

            _submitted = true;
            _owner.WriteOutputBytes(_path, GetBuffer().AsSpan(0, checked((int)Length)));
        }

    }

    private string ReadUniversal(BinaryReader reader, int length = -1)
    {
        return _unicode == true
            ? BinaryText.ReadUtf16(reader, length)
            : BinaryText.ReadAscii(reader, length);
    }

    private string ReadUniversalStop(BinaryReader reader, int length)
    {
        return _unicode == true
            ? BinaryText.ReadUtf16Stop(reader, length)
            : BinaryText.ReadAsciiStop(reader, length);
    }

    private string UniquePath(string path)
    {
        lock (_pathLock)
        {
            path = Path.GetFullPath(path);
            if (_usedPaths.Add(path))
                return path;

            string dir = Path.GetDirectoryName(path)!;
            string name = Path.GetFileNameWithoutExtension(path);
            string ext = Path.GetExtension(path);
            for (int i = 1; ; i++)
            {
                string candidate = Path.Combine(dir, $"{name}_{i}{ext}");
                if (_usedPaths.Add(candidate))
                    return candidate;
            }
        }
    }

    private (string FxPath, string XmlPath) UniqueShaderPaths(string directory, string baseName, string fxExtension)
    {
        lock (_pathLock)
        {
            directory = Path.GetFullPath(directory);
            for (int i = 0; ; i++)
            {
                string suffix = i == 0 ? string.Empty : "_" + i.ToString(CultureInfo.InvariantCulture);
                string fxPath = Path.Combine(directory, $"{baseName}{suffix}{fxExtension}");
                string xmlPath = Path.Combine(directory, $"{baseName}{suffix}.xml");
                if (_usedPaths.Contains(fxPath) || _usedPaths.Contains(xmlPath))
                    continue;

                _usedPaths.Add(fxPath);
                _usedPaths.Add(xmlPath);
                return (fxPath, xmlPath);
            }
        }
    }
}

internal enum ImagePayloadKind
{
    Standard,
    Optimized25Plus
}

internal sealed class RentedBuffer : IDisposable
{
    private byte[]? _buffer;

    private RentedBuffer(byte[] buffer, int length)
    {
        _buffer = buffer;
        Length = length;
    }

    public byte[] Buffer => _buffer ?? throw new ObjectDisposedException(nameof(RentedBuffer));
    public int Length { get; private set; }

    public static RentedBuffer Rent(int length)
    {
        if (length < 0)
            throw new InvalidDataException($"Cannot rent a negative byte count: {length}");

        return new RentedBuffer(ArrayPool<byte>.Shared.Rent(length), length);
    }

    public void Resize(int length)
    {
        if (length < 0 || length > Buffer.Length)
            throw new InvalidDataException($"Cannot resize rented buffer to {length} bytes; capacity is {Buffer.Length}.");

        Length = length;
    }

    public void Dispose()
    {
        byte[]? buffer = _buffer;
        if (buffer == null)
            return;

        _buffer = null;
        Length = 0;
        ArrayPool<byte>.Shared.Return(buffer);
    }
}

internal sealed class ImageExportJob
{
    public ImagePayloadKind Kind;
    public uint Handle;
    public int Checksum;
    public int References;
    public int DecompressedSize;
    public short Width;
    public short Height;
    public byte GraphicMode;
    public byte Flags;
    public short HotspotX;
    public short HotspotY;
    public short ActionPointX;
    public short ActionPointY;
    public Color TransparentColor = Color.Black;
    public RentedBuffer? Payload;

    public void Clear()
    {
        Payload?.Dispose();
        Payload = null;
    }
}

internal sealed class SoundExportJob
{
    public uint Handle;
    public int Checksum;
    public uint References;
    public int DecompressedSize;
    public uint Flags;
    public int Frequency;
    public int NameLength;
    public bool PlayFromDisk;
    public byte[] Payload = Array.Empty<byte>();

    public void Clear() => Payload = Array.Empty<byte>();
}

internal sealed class LiteImage
{
    public uint Handle;
    public int Checksum;
    public int References;
    public short Width;
    public short Height;
    public byte GraphicMode;
    public byte Flags;
    public short HotspotX;
    public short HotspotY;
    public short ActionPointX;
    public short ActionPointY;
    public Color TransparentColor = Color.Black;
    public byte[] ImageData = Array.Empty<byte>();
    public int ImageDataOffset;
    public int ImageDataLength;
    private RentedBuffer? _imageDataOwner;

    public bool Flag(int bit) => BitFlag.IsSet(Flags, bit);

    public void SetImageData(RentedBuffer owner, int offset, int length)
    {
        if (offset < 0 || length < 0 || offset > owner.Length - length)
            throw new InvalidDataException($"Image data range is outside the rented buffer: offset={offset}, length={length}, buffer={owner.Length}.");

        _imageDataOwner = owner;
        ImageData = owner.Buffer;
        ImageDataOffset = offset;
        ImageDataLength = length;
    }

    public void SetImageData(byte[] data, int offset, int length)
    {
        if (offset < 0 || length < 0 || offset > data.Length - length)
            throw new InvalidDataException($"Image data range is outside the buffer: offset={offset}, length={length}, buffer={data.Length}.");

        ImageData = data;
        ImageDataOffset = offset;
        ImageDataLength = length;
    }

    public void Clear()
    {
        _imageDataOwner?.Dispose();
        _imageDataOwner = null;
        ImageData = Array.Empty<byte>();
        ImageDataOffset = 0;
        ImageDataLength = 0;
    }
}

internal static class FastPngWriter
{
    private static readonly byte[] Signature = [137, 80, 78, 71, 13, 10, 26, 10];
    private static readonly byte[] Ihdr = Encoding.ASCII.GetBytes("IHDR");
    private static readonly byte[] Idat = Encoding.ASCII.GetBytes("IDAT");
    private static readonly byte[] Iend = Encoding.ASCII.GetBytes("IEND");
    private static readonly PngCompressionSettings PngCompression = GetPngCompressionSettings();
    private static int _libDeflateUnavailable;
    [ThreadStatic]
    private static LibDeflate.ZlibCompressor? _threadLibDeflateCompressor;
    [ThreadStatic]
    private static int _threadLibDeflateLevel;

    public static void WriteImage(string path, LiteImage image, TranslatorContext context)
    {
        using RentedBuffer png = EncodeImage(image, context);
        using var handle = File.OpenHandle(
            path,
            FileMode.Create,
            FileAccess.Write,
            FileShare.None,
            FileOptions.SequentialScan,
            preallocationSize: png.Length);
        RandomAccess.Write(handle, png.Buffer.AsSpan(0, png.Length), 0);
    }

    public static RentedBuffer EncodeImage(LiteImage image, TranslatorContext context)
    {
        if (ImageTranslator.TryBuildFilteredRgbaScanlines(image, context, out RentedBuffer? filteredRgba, out bool fullyOpaque) &&
            filteredRgba != null)
        {
            using (filteredRgba)
            {
                if (fullyOpaque)
                {
                    using RentedBuffer filteredRgb = PackFilteredRgbaToRgb(image.Width, image.Height, filteredRgba.Buffer.AsSpan(0, filteredRgba.Length));
                    return EncodeFilteredScanlines(image.Width, image.Height, colorType: 2, filteredRgb.Buffer.AsSpan(0, filteredRgb.Length));
                }

                return EncodeFilteredScanlines(image.Width, image.Height, colorType: 6, filteredRgba.Buffer.AsSpan(0, filteredRgba.Length));
            }
        }

        int rgbaSize = checked(image.Width * image.Height * 4);
        byte[] rgba = ArrayPool<byte>.Shared.Rent(rgbaSize);
        try
        {
            ImageTranslator.WriteToRgba(image, context, rgba);
            bool omitAlpha = ImageTranslator.IsFullyOpaque(image.Width, image.Height, rgba);
            return EncodeRgba(image.Width, image.Height, rgba, omitAlpha);
        }
        finally
        {
            ArrayPool<byte>.Shared.Return(rgba);
        }
    }

    public static void WriteRgba(string path, int width, int height, byte[] rgba, bool omitAlpha)
    {
        using RentedBuffer png = EncodeRgba(width, height, rgba, omitAlpha);
        using var handle = File.OpenHandle(
            path,
            FileMode.Create,
            FileAccess.Write,
            FileShare.None,
            FileOptions.SequentialScan,
            preallocationSize: png.Length);
        RandomAccess.Write(handle, png.Buffer.AsSpan(0, png.Length), 0);
    }

    public static RentedBuffer EncodeRgba(int width, int height, byte[] rgba, bool omitAlpha)
    {
        if (width <= 0 || height <= 0)
            throw new InvalidDataException("PNG has invalid dimensions.");

        int sourceRowBytes = checked(width * 4);
        int pixelBytes = checked(sourceRowBytes * height);
        if (rgba.Length < pixelBytes)
            throw new ArgumentException($"RGBA buffer is too small: {rgba.Length} < {pixelBytes}.", nameof(rgba));

        using RentedBuffer filtered = BuildFilteredScanlines(width, height, sourceRowBytes, rgba, omitAlpha);
        return EncodeFilteredScanlines(width, height, omitAlpha ? (byte)2 : (byte)6, filtered.Buffer.AsSpan(0, filtered.Length));
    }

    private static RentedBuffer EncodeFilteredScanlines(int width, int height, byte colorType, ReadOnlySpan<byte> filteredScanlines)
    {
        Span<byte> ihdrData = stackalloc byte[13];
        BinaryPrimitives.WriteUInt32BigEndian(ihdrData[0..4], (uint)width);
        BinaryPrimitives.WriteUInt32BigEndian(ihdrData[4..8], (uint)height);
        ihdrData[8] = 8; // bit depth
        ihdrData[9] = colorType; // RGB or RGBA

        using RentedBuffer idat = CompressScanlines(filteredScanlines);
        int pngLength = checked(Signature.Length + ChunkLength(ihdrData.Length) + ChunkLength(idat.Length) + ChunkLength(0));
        RentedBuffer png = RentedBuffer.Rent(pngLength);
        int offset = 0;
        try
        {
            Span<byte> destination = png.Buffer.AsSpan(0, png.Length);
            Signature.CopyTo(destination[offset..]);
            offset += Signature.Length;
            WriteChunk(destination, ref offset, Ihdr, ihdrData);
            WriteChunk(destination, ref offset, Idat, idat.Buffer.AsSpan(0, idat.Length));
            WriteChunk(destination, ref offset, Iend, ReadOnlySpan<byte>.Empty);
            Debug.Assert(offset == png.Length);
            return png;
        }
        catch
        {
            png.Dispose();
            throw;
        }
    }

    private static RentedBuffer CompressScanlines(ReadOnlySpan<byte> input)
    {
        if (TryCompressWithLibDeflate(input, out RentedBuffer? libDeflateCompressed) && libDeflateCompressed != null)
            return libDeflateCompressed;

        RentedBuffer compressed = RentedBuffer.Rent(GetZlibUpperBound(input.Length));
        try
        {
            using MemoryStream output = new(compressed.Buffer, 0, compressed.Length, writable: true, publiclyVisible: true);
            using (ZLibStream zlib = new(output, PngCompression.ManagedLevel, leaveOpen: true))
            {
                zlib.Write(input);
            }

            compressed.Resize(checked((int)output.Position));
            return compressed;
        }
        catch
        {
            compressed.Dispose();
            throw;
        }
    }

    private static RentedBuffer BuildFilteredScanlines(int width, int height, int sourceRowBytes, byte[] rgba, bool omitAlpha)
    {
        int pngPixelBytes = omitAlpha ? 3 : 4;
        int rowBytes = checked(width * pngPixelBytes);
        int filteredLength = checked((rowBytes + 1) * height);
        RentedBuffer filtered = RentedBuffer.Rent(filteredLength);
        Span<byte> output = filtered.Buffer.AsSpan(0, filtered.Length);

        for (int y = 0; y < height; y++)
        {
            int rowStart = y * (rowBytes + 1);
            output[rowStart] = 0; // PNG filter type: None
            int source = y * sourceRowBytes;
            Span<byte> row = output.Slice(rowStart + 1, rowBytes);
            if (omitAlpha)
            {
                int dest = 0;
                int rowEnd = source + sourceRowBytes;
                while (source < rowEnd)
                {
                    row[dest++] = rgba[source++];
                    row[dest++] = rgba[source++];
                    row[dest++] = rgba[source++];
                    source++;
                }
            }
            else
            {
                rgba.AsSpan(source, rowBytes).CopyTo(row);
            }
        }

        return filtered;
    }

    private static RentedBuffer PackFilteredRgbaToRgb(int width, int height, ReadOnlySpan<byte> rgba)
    {
        int rgbaRowBytes = checked(width * 4);
        int rgbRowBytes = checked(width * 3);
        int expectedLength = checked((rgbaRowBytes + 1) * height);
        if (rgba.Length < expectedLength)
            throw new ArgumentException($"Filtered RGBA buffer is too small: {rgba.Length} < {expectedLength}.", nameof(rgba));

        RentedBuffer rgb = RentedBuffer.Rent(checked((rgbRowBytes + 1) * height));
        Span<byte> output = rgb.Buffer.AsSpan(0, rgb.Length);
        for (int y = 0; y < height; y++)
        {
            int source = y * (rgbaRowBytes + 1);
            int dest = y * (rgbRowBytes + 1);
            output[dest++] = rgba[source++];
            int rowEnd = source + rgbaRowBytes;
            while (source < rowEnd)
            {
                output[dest++] = rgba[source++];
                output[dest++] = rgba[source++];
                output[dest++] = rgba[source++];
                source++;
            }
        }

        return rgb;
    }

    private static bool TryCompressWithLibDeflate(ReadOnlySpan<byte> input, out RentedBuffer? compressed)
    {
        compressed = null;
        if (!PngCompression.UseLibDeflate || Volatile.Read(ref _libDeflateUnavailable) != 0)
            return false;

        RentedBuffer? output = null;
        try
        {
            LibDeflate.ZlibCompressor compressor = GetThreadLibDeflateCompressor();
            output = RentedBuffer.Rent(compressor.GetBound(input.Length));
            int written = compressor.Compress(input, output.Buffer.AsSpan(0, output.Length));
            if (written <= 0)
                throw new InvalidDataException("libdeflate returned an empty PNG IDAT stream.");

            output.Resize(written);
            compressed = output;
            output = null;
            return true;
        }
        catch (Exception ex) when (IsLibDeflateUnavailable(ex))
        {
            Volatile.Write(ref _libDeflateUnavailable, 1);
            return false;
        }
        finally
        {
            output?.Dispose();
        }
    }

    private static LibDeflate.ZlibCompressor GetThreadLibDeflateCompressor()
    {
        LibDeflate.ZlibCompressor? compressor = _threadLibDeflateCompressor;
        if (compressor != null && _threadLibDeflateLevel == PngCompression.LibDeflateLevel)
            return compressor;

        compressor?.Dispose();
        compressor = new LibDeflate.ZlibCompressor(PngCompression.LibDeflateLevel);
        _threadLibDeflateCompressor = compressor;
        _threadLibDeflateLevel = PngCompression.LibDeflateLevel;
        return compressor;
    }

    private static bool IsLibDeflateUnavailable(Exception ex) =>
        ex is DllNotFoundException or EntryPointNotFoundException or BadImageFormatException
        || ex.InnerException != null && IsLibDeflateUnavailable(ex.InnerException);

    private static int ChunkLength(int dataLength) => checked(dataLength + 12);

    private static int GetZlibUpperBound(int length)
    {
        long bound = length + length / 4_096L + length / 16_384L + 13 + 64;
        if (bound > int.MaxValue)
            throw new InvalidDataException($"PNG IDAT input is too large to compress in memory: {length} bytes.");

        return Math.Max(256, (int)bound);
    }

    private static void WriteChunk(Span<byte> output, ref int offset, ReadOnlySpan<byte> type, ReadOnlySpan<byte> data)
    {
        BinaryPrimitives.WriteUInt32BigEndian(output.Slice(offset, 4), (uint)data.Length);
        offset += 4;
        type.CopyTo(output.Slice(offset, 4));
        offset += 4;
        data.CopyTo(output.Slice(offset, data.Length));
        offset += data.Length;
        BinaryPrimitives.WriteUInt32BigEndian(output.Slice(offset, 4), ComputeCrc(type, data));
        offset += 4;
    }

    private static uint ComputeCrc(ReadOnlySpan<byte> type, ReadOnlySpan<byte> data)
    {
        Crc32 crc = new();
        crc.Append(type);
        crc.Append(data);
        return crc.GetCurrentHashAsUInt32();
    }

    private static PngCompressionSettings GetPngCompressionSettings()
    {
        string? value = Environment.GetEnvironmentVariable("FUSION_ASSET_LITE_PNG_COMPRESSION");
        return value?.Trim().ToUpperInvariant() switch
        {
            "NONE" or "NO_COMPRESSION" or "STORED" or "0" => new(false, 0, CompressionLevel.NoCompression),
            "ZLIB" or "MANAGED" or "ZLIB_FAST" or "ZLIB_FASTEST" or "MANAGED_FAST" or "MANAGED_FASTEST" => new(false, 1, CompressionLevel.Fastest),
            "ZLIB_OPTIMAL" or "MANAGED_OPTIMAL" => new(false, 6, CompressionLevel.Optimal),
            "OPTIMAL" or "SMALL" or "SMALLEST" or "6" => new(true, 6, CompressionLevel.Optimal),
            null or "" or "FAST" or "FASTEST" or "LIBDEFLATE" or "LIBDEFLATE_FAST" or "LIBDEFLATE_FASTEST" or "1" => new(true, 1, CompressionLevel.Fastest),
            _ => UseDefaultPngCompressionForUnknownValue(value)
        };
    }

    private static PngCompressionSettings UseDefaultPngCompressionForUnknownValue(string? value)
    {
        Console.Error.WriteLine($"Warning: unknown FUSION_ASSET_LITE_PNG_COMPRESSION value '{value}'; using LIBDEFLATE_FASTEST.");
        return new PngCompressionSettings(true, 1, CompressionLevel.Fastest);
    }

    private readonly record struct PngCompressionSettings(bool UseLibDeflate, int LibDeflateLevel, CompressionLevel ManagedLevel);
}

internal static class ImageFlags
{
    public const int Rle = 0;
    public const int Rlew = 1;
    public const int Rlet = 2;
    public const int Lzx = 3;
    public const int Alpha = 4;
    public const int Rgba = 7;
}

internal readonly record struct TranslatorContext(int Build, float Fusion, bool Plus, bool Seeded, bool PremultipliedAlpha);

internal static class ImageTranslator
{
    private static readonly byte[] PremultipliedAlphaLookup = BuildPremultipliedAlphaLookup();
    private static readonly Vector128<byte> RgbToBgraShuffleMask = Vector128.Create(
        (byte)2, 1, 0, 0x80,
        5, 4, 3, 0x80,
        8, 7, 6, 0x80,
        11, 10, 9, 0x80);
    private static readonly Vector128<byte> RgbToRgbaShuffleMask = Vector128.Create(
        (byte)0, 1, 2, 0x80,
        3, 4, 5, 0x80,
        6, 7, 8, 0x80,
        9, 10, 11, 0x80);
    private static readonly Vector128<byte> RgbaAlphaMask = Vector128.Create(
        (byte)0, 0, 0, 255,
        0, 0, 0, 255,
        0, 0, 0, 255,
        0, 0, 0, 255);

    public static void WriteToRgba(LiteImage image, TranslatorContext context, byte[] output)
    {
        if (image.Width <= 0 || image.Height <= 0)
            throw new InvalidDataException("Image has invalid dimensions.");

        int requiredSize = checked(image.Width * image.Height * 4);
        if (output.Length < requiredSize)
            throw new ArgumentException($"Output buffer is too small: {output.Length} < {requiredSize}.", nameof(output));

        switch (image.GraphicMode)
        {
            case 4:
                Normal24BitMaskedToRgba(image, context, output);
                break;
            case 6:
                Normal15BitToRgba(image, context, output);
                break;
            case 7:
                Normal16BitToRgba(image, context, output);
                break;
            case 8:
                TwoFivePlusToRgba(image, context, output);
                break;
            default:
                throw new NotSupportedException($"Graphic mode {image.GraphicMode} is not implemented.");
        }
    }

    public static bool TryBuildFilteredRgbaScanlines(LiteImage image, TranslatorContext context, out RentedBuffer? filtered, out bool fullyOpaque)
    {
        filtered = null;
        fullyOpaque = true;
        if (image.GraphicMode != 4 || IsRle(image))
            return false;

        if (image.Width <= 0 || image.Height <= 0)
            throw new InvalidDataException("Image has invalid dimensions.");

        int rowBytes = checked(image.Width * 4);
        filtered = RentedBuffer.Rent(checked((rowBytes + 1) * image.Height));
        try
        {
            BuildNormal24BitMaskedFilteredRgba(image, context, filtered.Buffer.AsSpan(0, filtered.Length), out fullyOpaque);
            return true;
        }
        catch
        {
            filtered.Dispose();
            filtered = null;
            throw;
        }
    }

    public static bool IsFullyOpaque(int width, int height, byte[] rgba)
    {
        int pixelBytes = checked(width * height * 4);
        if (rgba.Length < pixelBytes)
            throw new ArgumentException($"RGBA buffer is too small: {rgba.Length} < {pixelBytes}.", nameof(rgba));

        for (int i = 3; i < pixelBytes; i += 4)
        {
            if (rgba[i] != 255)
                return false;
        }

        return true;
    }

    private static int GetPadding(LiteImage image, TranslatorContext context)
    {
        int colorModeSize = 3;
        int modSize = 2;
        switch (image.GraphicMode)
        {
            case 3:
                colorModeSize = 1;
                modSize = 4;
                break;
            case 6:
            case 7:
                colorModeSize = 2;
                break;
            case 0:
            case 8:
                colorModeSize = 4;
                break;
        }

        if (!image.Flag(ImageFlags.Rlet) || context.Plus || context.Fusion < 2.0f)
            return image.Width * colorModeSize % modSize;
        if (context.Build < 280)
            return image.Width * colorModeSize % modSize * colorModeSize;
        return image.Width % modSize * colorModeSize;
    }

    private static int GetAlphaPadding(LiteImage image) => (4 - image.Width % 4) % 4;

    private static bool IsRle(LiteImage image) =>
        image.Flag(ImageFlags.Rle) || image.Flag(ImageFlags.Rlew) || image.Flag(ImageFlags.Rlet);

    private static byte PeekImageByte(LiteImage image, int position)
    {
        EnsureImageBytes(image, position, 1);
        return image.ImageData[image.ImageDataOffset + position];
    }

    private static byte ReadImageByte(LiteImage image, ref int position)
    {
        EnsureImageBytes(image, position, 1);
        return image.ImageData[image.ImageDataOffset + position++];
    }

    private static ushort ReadImageUInt16(LiteImage image, ref int position)
    {
        EnsureImageBytes(image, position, 2);
        int source = image.ImageDataOffset + position;
        ushort value = (ushort)(image.ImageData[source] | image.ImageData[source + 1] << 8);
        position += 2;
        return value;
    }

    private static void SkipImageBytes(LiteImage image, ref int position, int count)
    {
        EnsureImageBytes(image, position, count);
        position += count;
    }

    private static void EnsureImageBytes(LiteImage image, int position, int count)
    {
        if (position < 0 || count < 0 || position > image.ImageDataLength - count)
            throw new InvalidDataException($"Image data is truncated at offset {position}; needed {count} bytes, length is {image.ImageDataLength}.");
    }

    private static void Normal24BitMaskedToRgba(LiteImage image, TranslatorContext context, byte[] output)
    {
        int stride = image.Width * 4;
        int pad = GetPadding(image, context);
        int position = 0;
        int command = PeekImageByte(image, position);
        bool rleLoop = false;
        bool rleCommander = false;
        bool rle = IsRle(image);
        bool hasAlpha = image.Flag(ImageFlags.Alpha);
        bool fusion3Unseeded = Math.Abs(context.Fusion - 3.0f) < 0.01f && !context.Seeded;
        byte transparentR = image.TransparentColor.R;
        byte transparentG = image.TransparentColor.G;
        byte transparentB = image.TransparentColor.B;

        if (command > 128)
        {
            command -= 128;
            rleCommander = true;
        }
        else if (command == 0)
        {
            rleLoop = true;
        }

        if (rle)
            position++;

        if (!rle)
        {
            Normal24BitMaskedRowsToRgba(image, output, pad, hasAlpha, fusion3Unseeded, transparentR, transparentG, transparentB, ref position);
            return;
        }

        byte r = 0;
        byte g = 0;
        byte b = 0;
        for (int y = 0; y < image.Height; y++)
        {
            int newPos = y * stride;
            for (int x = 0; x < image.Width; x++)
            {
                if (!rle || !rleLoop || rleCommander)
                {
                    r = ReadImageByte(image, ref position);
                    g = ReadImageByte(image, ref position);
                    b = ReadImageByte(image, ref position);
                    rleLoop = true;
                }

                if (fusion3Unseeded)
                {
                    output[newPos + 0] = r;
                    output[newPos + 1] = g;
                    output[newPos + 2] = b;
                }
                else
                {
                    output[newPos + 0] = b;
                    output[newPos + 1] = g;
                    output[newPos + 2] = r;
                }

                output[newPos + 3] = 255;
                if (!hasAlpha &&
                    output[newPos + 0] == transparentR &&
                    output[newPos + 1] == transparentG &&
                    output[newPos + 2] == transparentB)
                {
                    output[newPos + 3] = 0;
                }

                newPos += 4;
                if (rle && --command == 0)
                {
                    command = ReadImageByte(image, ref position);
                    rleCommander = false;
                    rleLoop = false;

                    if (command > 128)
                    {
                        command -= 128;
                        rleCommander = true;
                    }
                    else if (command == 0)
                    {
                        rleLoop = true;
                    }
                }
            }

            SkipImageBytes(image, ref position, pad * 3);
        }

        if (hasAlpha)
        {
            int alphaPad = GetAlphaPadding(image);
            for (int y = 0; y < image.Height; y++)
            {
                for (int x = 0; x < image.Width; x++)
                    output[y * stride + x * 4 + 3] = ReadImageByte(image, ref position);
                SkipImageBytes(image, ref position, alphaPad);
            }
        }
    }

    private static void Normal24BitMaskedRowsToRgba(
        LiteImage image,
        byte[] output,
        int pad,
        bool hasAlpha,
        bool fusion3Unseeded,
        byte transparentR,
        byte transparentG,
        byte transparentB,
        ref int position)
    {
        int stride = image.Width * 4;
        int sourceOffset = image.ImageDataOffset;
        int rowBytes = checked(image.Width * 3);
        int rowSkip = checked(rowBytes + pad * 3);
        for (int y = 0; y < image.Height; y++)
        {
            EnsureImageBytes(image, position, rowSkip);
            int source = sourceOffset + position;
            int dest = y * stride;
            int rowEnd = source + rowBytes;
            int vectorPixels = Copy24BitMaskedRowVectorized(
                image.ImageData,
                source,
                output,
                dest,
                image.Width,
                rowBytes,
                hasAlpha,
                fusion3Unseeded,
                transparentR,
                transparentG,
                transparentB,
                out _);
            source += vectorPixels * 3;
            dest += vectorPixels * 4;
            while (source < rowEnd)
            {
                byte r = image.ImageData[source++];
                byte g = image.ImageData[source++];
                byte b = image.ImageData[source++];
                if (fusion3Unseeded)
                {
                    output[dest + 0] = r;
                    output[dest + 1] = g;
                    output[dest + 2] = b;
                }
                else
                {
                    output[dest + 0] = b;
                    output[dest + 1] = g;
                    output[dest + 2] = r;
                }

                output[dest + 3] = 255;
                if (!hasAlpha &&
                    output[dest + 0] == transparentR &&
                    output[dest + 1] == transparentG &&
                    output[dest + 2] == transparentB)
                {
                    output[dest + 3] = 0;
                }

                dest += 4;
            }

            position += rowSkip;
        }

        if (hasAlpha)
        {
            int alphaPad = GetAlphaPadding(image);
            int alphaSkip = checked(image.Width + alphaPad);
            for (int y = 0; y < image.Height; y++)
            {
                EnsureImageBytes(image, position, alphaSkip);
                int source = sourceOffset + position;
                int dest = y * stride + 3;
                for (int x = 0; x < image.Width; x++)
                {
                    output[dest] = image.ImageData[source++];
                    dest += 4;
                }

                position += alphaSkip;
            }
        }
    }

    private static void BuildNormal24BitMaskedFilteredRgba(
        LiteImage image,
        TranslatorContext context,
        Span<byte> output,
        out bool fullyOpaque)
    {
        int rowBytes = checked(image.Width * 4);
        int required = checked((rowBytes + 1) * image.Height);
        if (output.Length < required)
            throw new ArgumentException($"Filtered output buffer is too small: {output.Length} < {required}.", nameof(output));

        int pad = GetPadding(image, context);
        int position = 0;
        int sourceOffset = image.ImageDataOffset;
        int sourceRowBytes = checked(image.Width * 3);
        int sourceRowSkip = checked(sourceRowBytes + pad * 3);
        bool hasAlpha = image.Flag(ImageFlags.Alpha);
        bool fusion3Unseeded = Math.Abs(context.Fusion - 3.0f) < 0.01f && !context.Seeded;
        byte transparentR = image.TransparentColor.R;
        byte transparentG = image.TransparentColor.G;
        byte transparentB = image.TransparentColor.B;
        fullyOpaque = true;

        for (int y = 0; y < image.Height; y++)
        {
            EnsureImageBytes(image, position, sourceRowSkip);
            int rowStart = y * (rowBytes + 1);
            output[rowStart] = 0;
            int source = sourceOffset + position;
            int dest = rowStart + 1;
            int rowEnd = source + sourceRowBytes;
            int vectorPixels = Copy24BitMaskedRowVectorized(
                image.ImageData,
                source,
                output,
                dest,
                image.Width,
                sourceRowBytes,
                hasAlpha,
                fusion3Unseeded,
                transparentR,
                transparentG,
                transparentB,
                out bool vectorFullyOpaque);
            if (!vectorFullyOpaque)
                fullyOpaque = false;

            source += vectorPixels * 3;
            dest += vectorPixels * 4;
            while (source < rowEnd)
            {
                byte r = image.ImageData[source++];
                byte g = image.ImageData[source++];
                byte b = image.ImageData[source++];
                if (fusion3Unseeded)
                {
                    output[dest + 0] = r;
                    output[dest + 1] = g;
                    output[dest + 2] = b;
                }
                else
                {
                    output[dest + 0] = b;
                    output[dest + 1] = g;
                    output[dest + 2] = r;
                }

                output[dest + 3] = 255;
                if (!hasAlpha &&
                    output[dest + 0] == transparentR &&
                    output[dest + 1] == transparentG &&
                    output[dest + 2] == transparentB)
                {
                    output[dest + 3] = 0;
                    fullyOpaque = false;
                }

                dest += 4;
            }

            position += sourceRowSkip;
        }

        if (!hasAlpha)
            return;

        int alphaPad = GetAlphaPadding(image);
        int alphaSkip = checked(image.Width + alphaPad);
        for (int y = 0; y < image.Height; y++)
        {
            EnsureImageBytes(image, position, alphaSkip);
            int source = sourceOffset + position;
            int dest = y * (rowBytes + 1) + 1 + 3;
            for (int x = 0; x < image.Width; x++)
            {
                byte alpha = image.ImageData[source++];
                output[dest] = alpha;
                if (alpha != 255)
                    fullyOpaque = false;
                dest += 4;
            }

            position += alphaSkip;
        }
    }

    private static int Copy24BitMaskedRowVectorized(
        byte[] input,
        int source,
        Span<byte> output,
        int dest,
        int width,
        int rowBytes,
        bool hasAlpha,
        bool fusion3Unseeded,
        byte transparentR,
        byte transparentG,
        byte transparentB,
        out bool fullyOpaque)
    {
        fullyOpaque = true;
        if (!Ssse3.IsSupported || width < 6)
            return 0;

        int vectorGroups = Math.Min(width / 4, Math.Max(0, (rowBytes - 4) / 12));
        if (vectorGroups <= 0)
            return 0;

        Vector128<byte> shuffle = fusion3Unseeded ? RgbToRgbaShuffleMask : RgbToBgraShuffleMask;
        Vector128<byte> transparent = Vector128.Create(
            transparentR, transparentG, transparentB, (byte)255,
            transparentR, transparentG, transparentB, (byte)255,
            transparentR, transparentG, transparentB, (byte)255,
            transparentR, transparentG, transparentB, (byte)255);
        ref byte inputRef = ref MemoryMarshal.GetArrayDataReference(input);
        ref byte outputRef = ref MemoryMarshal.GetReference(output);

        int currentSource = source;
        int currentDest = dest;
        for (int group = 0; group < vectorGroups; group++)
        {
            Vector128<byte> sourceVector = Vector128.LoadUnsafe(ref Unsafe.Add(ref inputRef, currentSource));
            Vector128<byte> rgba = Sse2.Or(Ssse3.Shuffle(sourceVector, shuffle), RgbaAlphaMask);
            rgba.StoreUnsafe(ref Unsafe.Add(ref outputRef, currentDest));

            if (!hasAlpha)
            {
                int transparentMask = Sse2.MoveMask(Sse2.CompareEqual(rgba, transparent));
                if ((transparentMask & 0x000F) == 0x000F)
                {
                    output[currentDest + 3] = 0;
                    fullyOpaque = false;
                }
                if ((transparentMask & 0x00F0) == 0x00F0)
                {
                    output[currentDest + 7] = 0;
                    fullyOpaque = false;
                }
                if ((transparentMask & 0x0F00) == 0x0F00)
                {
                    output[currentDest + 11] = 0;
                    fullyOpaque = false;
                }
                if ((transparentMask & 0xF000) == 0xF000)
                {
                    output[currentDest + 15] = 0;
                    fullyOpaque = false;
                }
            }

            currentSource += 12;
            currentDest += 16;
        }

        return vectorGroups * 4;
    }

    private static void Normal16BitToRgba(LiteImage image, TranslatorContext context, byte[] output)
    {
        int stride = image.Width * 4;
        int pad = GetPadding(image, context);
        int position = 0;
        int command = PeekImageByte(image, position);
        bool rleLoop = false;
        bool rleCommander = false;
        bool rle = IsRle(image);

        if (command > 128)
        {
            command -= 128;
            rleCommander = true;
        }
        else if (command == 0)
        {
            rleLoop = true;
        }

        if (rle)
            position++;

        byte r = 0;
        byte g = 0;
        byte b = 0;
        for (int y = 0; y < image.Height; y++)
        {
            for (int x = 0; x < image.Width; x++)
            {
                if (!rle || !rleLoop || rleCommander)
                {
                    ushort value = ReadImageUInt16(image, ref position);
                    r = (byte)((value & 63488) >> 11);
                    g = (byte)((value & 2016) >> 5);
                    b = (byte)(value & 31);
                    r = (byte)(r << 3);
                    g = (byte)(g << 2);
                    b = (byte)(b << 3);
                    rleLoop = true;
                }

                int newPos = y * stride + x * 4;
                output[newPos + 0] = r;
                output[newPos + 1] = g;
                output[newPos + 2] = b;
                output[newPos + 3] = 255;
                if (!image.Flag(ImageFlags.Alpha) &&
                    output[newPos + 0] == image.TransparentColor.R &&
                    output[newPos + 1] == image.TransparentColor.G &&
                    output[newPos + 2] == image.TransparentColor.B)
                {
                    output[newPos + 3] = 0;
                }

                if (rle && --command == 0)
                {
                    command = ReadImageByte(image, ref position);
                    rleCommander = false;
                    rleLoop = false;
                    if (command > 128)
                    {
                        command -= 128;
                        rleCommander = true;
                    }
                    else if (command == 0)
                    {
                        rleLoop = true;
                    }
                }
            }

            SkipImageBytes(image, ref position, pad * 2);
        }

        ApplyTrailingAlpha(image, output, ref position, stride);
    }

    private static void Normal15BitToRgba(LiteImage image, TranslatorContext context, byte[] output)
    {
        int stride = image.Width * 4;
        int pad = GetPadding(image, context);
        int position = 0;
        int command = PeekImageByte(image, position);
        bool rleLoop = false;
        bool rleCommander = false;
        bool rle = IsRle(image);

        if (command > 128)
        {
            command -= 128;
            rleCommander = true;
        }
        else if (command == 0)
        {
            rleLoop = true;
        }

        if (rle)
            position++;

        byte r = 0;
        byte g = 0;
        byte b = 0;
        for (int y = 0; y < image.Height; y++)
        {
            for (int x = 0; x < image.Width; x++)
            {
                if (!rle || !rleLoop || rleCommander)
                {
                    ushort value = ReadImageUInt16(image, ref position);
                    r = (byte)((value & 31744) >> 10);
                    g = (byte)((value & 992) >> 5);
                    b = (byte)(value & 31);
                    r = (byte)(r << 3);
                    g = (byte)(g << 3);
                    b = (byte)(b << 3);
                    rleLoop = true;
                }

                int newPos = y * stride + x * 4;
                output[newPos + 0] = r;
                output[newPos + 1] = g;
                output[newPos + 2] = b;
                output[newPos + 3] = 255;
                if (!image.Flag(ImageFlags.Alpha) &&
                    output[newPos + 0] == image.TransparentColor.R &&
                    output[newPos + 1] == image.TransparentColor.G &&
                    output[newPos + 2] == image.TransparentColor.B)
                {
                    output[newPos + 3] = 0;
                }

                if (rle && --command == 0)
                {
                    command = ReadImageByte(image, ref position);
                    rleCommander = false;
                    rleLoop = false;
                    if (command > 128)
                    {
                        command -= 128;
                        rleCommander = true;
                    }
                    else if (command == 0)
                    {
                        rleLoop = true;
                    }
                }
            }

            SkipImageBytes(image, ref position, pad * 2);
        }

        ApplyTrailingAlpha(image, output, ref position, stride);
    }

    private static void TwoFivePlusToRgba(LiteImage image, TranslatorContext context, byte[] output)
    {
        byte[] imageData = image.ImageData;
        int imageDataOffset = image.ImageDataOffset;
        int stride = image.Width * 4;
        int pad = GetPadding(image, context);
        int position = 0;
        for (int y = 0; y < image.Height; y++)
        {
            for (int x = 0; x < image.Width; x++)
            {
                int newPos = y * stride + x * 4;
                EnsureImageBytes(image, position, 4);
                int source = imageDataOffset + position;
                if (Math.Abs(context.Fusion - 3.0f) < 0.01f && !context.Seeded)
                {
                    output[newPos + 0] = imageData[source + 0];
                    output[newPos + 1] = imageData[source + 1];
                    output[newPos + 2] = imageData[source + 2];
                }
                else
                {
                    output[newPos + 0] = imageData[source + 2];
                    output[newPos + 1] = imageData[source + 1];
                    output[newPos + 2] = imageData[source + 0];
                }

                output[newPos + 3] = 255;
                if (image.Flag(ImageFlags.Alpha) || image.Flag(ImageFlags.Rgba))
                {
                    if (context.PremultipliedAlpha && imageData[source + 3] != 0)
                    {
                        int alpha = imageData[source + 3];
                        output[newPos + 0] = PremultipliedAlphaLookup[(alpha << 8) | output[newPos + 0]];
                        output[newPos + 1] = PremultipliedAlphaLookup[(alpha << 8) | output[newPos + 1]];
                        output[newPos + 2] = PremultipliedAlphaLookup[(alpha << 8) | output[newPos + 2]];
                    }

                    output[newPos + 3] = imageData[source + 3];
                }
                else if (output[newPos + 0] == image.TransparentColor.R &&
                          output[newPos + 1] == image.TransparentColor.G &&
                          output[newPos + 2] == image.TransparentColor.B)
                {
                    output[newPos + 3] = 0;
                }

                position += 4;
            }

            SkipImageBytes(image, ref position, pad * 4);
        }

        if (position != image.ImageDataLength && image.Flag(ImageFlags.Alpha) && !image.Flag(ImageFlags.Rgba))
            ApplyTrailingAlpha(image, output, ref position, stride);
    }

    private static byte[] BuildPremultipliedAlphaLookup()
    {
        byte[] lookup = new byte[256 * 256];
        for (int alpha = 1; alpha <= 255; alpha++)
        {
            float alphaScale = alpha / 255f;
            for (int value = 0; value <= 255; value++)
                lookup[(alpha << 8) | value] = (byte)Math.Clamp(value / alphaScale, 0, 255);
        }

        return lookup;
    }

    private static void ApplyTrailingAlpha(LiteImage image, byte[] output, ref int position, int stride)
    {
        if (!image.Flag(ImageFlags.Alpha))
            return;

        int alphaPad = GetAlphaPadding(image);
        for (int y = 0; y < image.Height; y++)
        {
            for (int x = 0; x < image.Width; x++)
                output[y * stride + x * 4 + 3] = ReadImageByte(image, ref position);
            SkipImageBytes(image, ref position, alphaPad);
        }
    }
}

internal sealed class SoundAsset
{
    public uint Handle;
    public int Checksum;
    public uint References;
    public uint Flags;
    public int Frequency;
    public string Name = string.Empty;
    public byte[] Data = Array.Empty<byte>();
    public int DataOffset;
    public int DataLength;

    public string GetExtension()
    {
        ReadOnlySpan<byte> data = Data.AsSpan(DataOffset, DataLength);
        if (data.Length < 4)
            return "bin";

        string header = Encoding.ASCII.GetString(data[..4]);
        if (header == "RIFF")
            return "wav";
        if (header == "FORM")
            return "aiff";
        if (header == "OggS")
            return "ogg";
        if (data[0] == 'I' && data[1] == 'D' && data[2] == '3')
            return "mp3";
        if (data.Length >= 2 && data[0] == 0xFF && (data[1] & 0xE0) == 0xE0)
            return "mp3";
        if (header == "IMPM")
            return "it";
        if (data.Length >= 17 && Encoding.ASCII.GetString(data[..17]) == "Extended Module: ")
            return "xm";
        if (data.Length > 0x2F &&
            data[0x2C] == 'S' && data[0x2D] == 'C' && data[0x2E] == 'R' && data[0x2F] == 'M')
            return "s3m";
        if (data.Length > 0x43B)
        {
            string modSignature = Encoding.ASCII.GetString(data.Slice(0x438, 4));
            if (ModSignatures.Contains(modSignature))
                return "mod";
        }

        return "bin";
    }

    private static readonly HashSet<string> ModSignatures = new(StringComparer.Ordinal)
    {
        "2CHN", "M.K.", "6CHN", "8CHN", "10CH", "12CH", "14CH", "16CH",
        "18CH", "20CH", "22CH", "24CH", "26CH", "28CH", "30CH", "32CH",
        "M!K!", "FLT4", "OCTA"
    };
}

internal sealed class ShaderAsset
{
    public int Handle;
    public string Name = string.Empty;
    public bool Compiled;
    public byte[] FxData = Array.Empty<byte>();
    public List<ShaderParameter> Parameters { get; } = new();

    public string ToXml()
    {
        StringBuilder sb = new();
        sb.AppendLine("<effect>");
        sb.AppendLine($"  <name>{XmlEscape(Path.GetFileNameWithoutExtension(Name))}</name>");
        sb.AppendLine($"  <handle>{Handle}</handle>");
        sb.AppendLine($"  <compiled>{Compiled.ToString().ToLowerInvariant()}</compiled>");
        foreach (ShaderParameter parameter in Parameters)
            sb.AppendLine($"  <parameter type=\"{parameter.Type}\">{XmlEscape(parameter.Name)}</parameter>");
        sb.AppendLine("</effect>");
        return sb.ToString();
    }

    private static string XmlEscape(string? value) =>
        string.IsNullOrEmpty(value)
            ? string.Empty
            : value.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;").Replace("\"", "&quot;");
}

internal readonly record struct ShaderParameter(byte Type, string Name);

internal sealed class ProgressMeter
{
    private static readonly Process CurrentProcess = Process.GetCurrentProcess();

    private readonly string _label;
    private readonly int _total;
    private int _lastPercent = -1;

    public ProgressMeter(string label, int total)
    {
        _label = label;
        _total = Math.Max(1, total);
    }

    public void Step(int value)
    {
        int percent = value * 100 / _total;
        if (percent == _lastPercent || (percent % 10 != 0 && percent != 100))
            return;

        _lastPercent = percent;
        CurrentProcess.Refresh();
        long ram = CurrentProcess.WorkingSet64 / 1024 / 1024;
        Console.WriteLine($"{_label}: {percent}% ({value}/{_total}), RAM {ram} MB");
    }

    public void Done() => Step(_total);
}

internal sealed class WindowedReadStream : Stream
{
    // This stream is a non-owning view over the package FileStream.
    private readonly Stream _baseStream;
    private readonly long _start;
    private readonly long _length;
    private long _position;

    public WindowedReadStream(Stream baseStream, long start, long length)
    {
        _baseStream = baseStream;
        _start = start;
        _length = length;
    }

    public override bool CanRead => true;
    public override bool CanSeek => true;
    public override bool CanWrite => false;
    public override long Length => _length;

    public override long Position
    {
        get => _position;
        set => Seek(value, SeekOrigin.Begin);
    }

    public override void Flush() { }

    public override int Read(byte[] buffer, int offset, int count)
    {
        if (_position >= _length)
            return 0;

        count = (int)Math.Min(count, _length - _position);
        long targetPosition = _start + _position;
        if (_baseStream.Position != targetPosition)
            _baseStream.Position = targetPosition;
        int read = _baseStream.Read(buffer, offset, count);
        _position += read;
        return read;
    }

    public override long Seek(long offset, SeekOrigin origin)
    {
        long newPosition = origin switch
        {
            SeekOrigin.Begin => offset,
            SeekOrigin.Current => _position + offset,
            SeekOrigin.End => _length + offset,
            _ => throw new ArgumentOutOfRangeException(nameof(origin))
        };

        if (newPosition < 0 || newPosition > _length)
            throw new IOException("Seek outside chunk window.");

        _position = newPosition;
        return _position;
    }

    public override void SetLength(long value) => throw new NotSupportedException();
    public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
}

internal sealed class ExactLengthReadStream : Stream
{
    private readonly Stream _inner;
    private readonly long _length;
    private long _position;

    public ExactLengthReadStream(Stream inner, long length)
    {
        if (length < 0)
            throw new ArgumentOutOfRangeException(nameof(length));

        _inner = inner;
        _length = length;
    }

    public override bool CanRead => true;
    public override bool CanSeek => true;
    public override bool CanWrite => false;
    public override long Length => _length;

    public override long Position
    {
        get => _position;
        set => Seek(value, SeekOrigin.Begin);
    }

    public override void Flush() { }

    public override int Read(byte[] buffer, int offset, int count)
    {
        ValidateBufferArguments(buffer, offset, count);
        return Read(buffer.AsSpan(offset, count));
    }

    public override int Read(Span<byte> buffer)
    {
        long remaining = _length - _position;
        if (remaining <= 0)
            return 0;

        if (buffer.Length > remaining)
            buffer = buffer[..(int)remaining];

        int read = _inner.Read(buffer);
        if (read == 0)
            throw new EndOfStreamException($"Decompressed chunk ended at {_position} bytes, expected {_length}.");

        _position += read;
        return read;
    }

    public override long Seek(long offset, SeekOrigin origin)
    {
        long target = origin switch
        {
            SeekOrigin.Begin => offset,
            SeekOrigin.Current => _position + offset,
            SeekOrigin.End => _length + offset,
            _ => throw new ArgumentOutOfRangeException(nameof(origin))
        };

        if (target < _position)
            throw new IOException("Cannot seek backwards in a streaming decompressed chunk.");
        if (target > _length)
            throw new IOException("Seek outside decompressed chunk.");

        SkipTo(target);
        return _position;
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
            _inner.Dispose();
        base.Dispose(disposing);
    }

    private void SkipTo(long target)
    {
        byte[] buffer = ArrayPool<byte>.Shared.Rent(81920);
        try
        {
            while (_position < target)
            {
                int read = Read(buffer.AsSpan(0, (int)Math.Min(buffer.Length, target - _position)));
                if (read == 0)
                    throw new EndOfStreamException($"Decompressed chunk ended at {_position} bytes, expected {target}.");
            }
        }
        finally
        {
            ArrayPool<byte>.Shared.Return(buffer);
        }
    }

    public override void SetLength(long value) => throw new NotSupportedException();
    public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
}

internal static class Compression
{
    private const int MaxDecompressedBlockBytes = 1024 * 1024 * 1024;
    private const int MaxInitialDecompressedCapacity = 8 * 1024 * 1024;
    private static int _libDeflateUnavailable;
    [ThreadStatic]
    private static LibDeflate.ZlibDecompressor? _threadZlibDecompressor;
    [ThreadStatic]
    private static LibDeflate.DeflateDecompressor? _threadDeflateDecompressor;

    public static RentedBuffer DecompressExact(byte[] data, int offset, int length, int exactDecompressedSize)
    {
        if (exactDecompressedSize < 0 || exactDecompressedSize > MaxDecompressedBlockBytes)
            throw new InvalidDataException($"Invalid decompressed size: {exactDecompressedSize}");
        if (offset < 0 || length < 0 || offset > data.Length - length)
            throw new InvalidDataException($"Compressed data range is outside the buffer: offset={offset}, length={length}, buffer={data.Length}.");

        if (TryLibDeflateDecompressExact(data.AsSpan(offset, length), exactDecompressedSize, out RentedBuffer? libDeflateOutput) &&
            libDeflateOutput != null)
        {
            return libDeflateOutput;
        }

        RentedBuffer output = RentedBuffer.Rent(exactDecompressedSize);
        try
        {
            using MemoryStream input = new(data, offset, length, writable: false, publiclyVisible: false);
            using Stream stream = IsZlib(data, offset, length)
                ? new ZLibStream(input, CompressionMode.Decompress)
                : new DeflateStream(input, CompressionMode.Decompress);

            int totalRead = 0;
            while (totalRead < exactDecompressedSize)
            {
                int read = stream.Read(output.Buffer, totalRead, exactDecompressedSize - totalRead);
                if (read == 0)
                    throw new EndOfStreamException($"Decompressed block ended at {totalRead} bytes, expected {exactDecompressedSize}.");

                totalRead += read;
            }

            if (stream.ReadByte() != -1)
                throw new InvalidDataException($"Decompressed block exceeds expected size of {exactDecompressedSize} bytes.");

            return output;
        }
        catch
        {
            output.Dispose();
            throw;
        }
    }

    public static byte[] DecompressExact(byte[] data, int exactDecompressedSize)
    {
        if (exactDecompressedSize < 0 || exactDecompressedSize > MaxDecompressedBlockBytes)
            throw new InvalidDataException($"Invalid decompressed size: {exactDecompressedSize}");

        if (TryLibDeflateDecompressExact(data, exactDecompressedSize, out RentedBuffer? libDeflateOutput) &&
            libDeflateOutput != null)
        {
            using (libDeflateOutput)
                return libDeflateOutput.Buffer.AsSpan(0, libDeflateOutput.Length).ToArray();
        }

        byte[] output = new byte[exactDecompressedSize];
        using MemoryStream input = new(data, writable: false);
        using Stream stream = IsZlib(data)
            ? new ZLibStream(input, CompressionMode.Decompress)
            : new DeflateStream(input, CompressionMode.Decompress);

        int totalRead = 0;
        while (totalRead < exactDecompressedSize)
        {
            int read = stream.Read(output, totalRead, exactDecompressedSize - totalRead);
            if (read == 0)
                throw new EndOfStreamException($"Decompressed block ended at {totalRead} bytes, expected {exactDecompressedSize}.");

            totalRead += read;
        }

        if (stream.ReadByte() != -1)
            throw new InvalidDataException($"Decompressed block exceeds expected size of {exactDecompressedSize} bytes.");

        return output;
    }

    private static bool TryLibDeflateDecompressExact(ReadOnlySpan<byte> data, int exactDecompressedSize, out RentedBuffer? output)
    {
        output = null;
        if (Volatile.Read(ref _libDeflateUnavailable) != 0)
            return false;

        RentedBuffer? rented = null;
        try
        {
            rented = RentedBuffer.Rent(exactDecompressedSize);
            int bytesWritten;
            OperationStatus status;
            status = IsZlib(data)
                ? GetThreadZlibDecompressor().Decompress(data, rented.Buffer.AsSpan(0, rented.Length), out bytesWritten, out _)
                : GetThreadDeflateDecompressor().Decompress(data, rented.Buffer.AsSpan(0, rented.Length), out bytesWritten, out _);

            if (status != OperationStatus.Done || bytesWritten != exactDecompressedSize)
                return false;

            output = rented;
            rented = null;
            return true;
        }
        catch (Exception ex) when (IsLibDeflateUnavailable(ex))
        {
            Volatile.Write(ref _libDeflateUnavailable, 1);
            return false;
        }
        finally
        {
            rented?.Dispose();
        }
    }

    private static bool IsLibDeflateUnavailable(Exception ex) =>
        ex is DllNotFoundException or EntryPointNotFoundException or BadImageFormatException
        || ex.InnerException != null && IsLibDeflateUnavailable(ex.InnerException);

    private static LibDeflate.ZlibDecompressor GetThreadZlibDecompressor() =>
        _threadZlibDecompressor ??= new LibDeflate.ZlibDecompressor();

    private static LibDeflate.DeflateDecompressor GetThreadDeflateDecompressor() =>
        _threadDeflateDecompressor ??= new LibDeflate.DeflateDecompressor();

    public static byte[] DecompressBlock(byte[] data, int maxOutputSize = MaxDecompressedBlockBytes)
    {
        return DecompressBlock(data, 0, data.Length, maxOutputSize);
    }

    public static byte[] DecompressBlock(byte[] data, int offset, int length, int maxOutputSize = MaxDecompressedBlockBytes)
    {
        if (maxOutputSize < 0)
            throw new InvalidDataException($"Invalid decompressed size: {maxOutputSize}");
        if (offset < 0 || length < 0 || offset > data.Length - length)
            throw new InvalidDataException($"Compressed data range is outside the buffer: offset={offset}, length={length}, buffer={data.Length}.");

        using MemoryStream input = new(data, offset, length, writable: false, publiclyVisible: false);
        using Stream stream = IsZlib(data, offset, length)
            ? new ZLibStream(input, CompressionMode.Decompress)
            : new DeflateStream(input, CompressionMode.Decompress);
        int capacity = maxOutputSize is > 0 and < MaxDecompressedBlockBytes
            ? Math.Min(maxOutputSize, MaxInitialDecompressedCapacity)
            : 0;
        using MemoryStream output = capacity > 0 ? new MemoryStream(capacity) : new MemoryStream();
        byte[] buffer = new byte[81920];
        int read;
        while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
        {
            if (output.Length + read > maxOutputSize)
                throw new InvalidDataException($"Decompressed block exceeds limit of {maxOutputSize} bytes.");

            output.Write(buffer, 0, read);
        }

        return output.ToArray();
    }

    public static void DecompressTo(Stream input, Stream output, int maxOutputSize = MaxDecompressedBlockBytes)
    {
        if (maxOutputSize < 0)
            throw new InvalidDataException($"Invalid decompressed size: {maxOutputSize}");

        using Stream stream = new ZLibStream(input, CompressionMode.Decompress, leaveOpen: true);
        byte[] buffer = ArrayPool<byte>.Shared.Rent(81920);
        long totalWritten = 0;
        try
        {
            int read;
            while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
            {
                if (totalWritten + read > maxOutputSize)
                    throw new InvalidDataException($"Decompressed block exceeds limit of {maxOutputSize} bytes.");

                output.Write(buffer, 0, read);
                totalWritten += read;
            }
        }
        finally
        {
            ArrayPool<byte>.Shared.Return(buffer);
        }
    }

    public static bool IsZlib(byte[] data)
    {
        if (data.Length < 2)
            return false;

        return IsZlib(data, 0, data.Length);
    }

    public static bool IsZlib(ReadOnlySpan<byte> data)
    {
        if (data.Length < 2)
            return false;

        return IsZlibHeader(data[0], data[1]);
    }

    public static bool IsZlib(byte[] data, int offset, int length)
    {
        if (length < 2)
            return false;

        return IsZlibHeader(data[offset], data[offset + 1]);
    }

    public static bool IsZlibHeader(byte cmf, byte flg)
    {
        if ((cmf & 0x0F) != 8 || (cmf >> 4) > 7)
            return false;

        return ((cmf << 8) + flg) % 31 == 0;
    }
}

internal static class BinaryReaderExtensions
{
    public static bool HasBytes(this BinaryReader reader, long count) =>
        reader.BaseStream.Length - reader.BaseStream.Position >= count;

    public static int PeekInt32(this BinaryReader reader)
    {
        long pos = reader.BaseStream.Position;
        int value = reader.ReadInt32();
        reader.BaseStream.Position = pos;
        return value;
    }

    public static uint PeekUInt32(this BinaryReader reader)
    {
        long pos = reader.BaseStream.Position;
        uint value = reader.ReadUInt32();
        reader.BaseStream.Position = pos;
        return value;
    }

    public static long PeekInt64(this BinaryReader reader)
    {
        long pos = reader.BaseStream.Position;
        long value = reader.ReadInt64();
        reader.BaseStream.Position = pos;
        return value;
    }

    public static string GetAsciiAt(this BinaryReader reader, long relativePosition, int length)
    {
        long pos = reader.BaseStream.Position;
        reader.BaseStream.Seek(relativePosition, SeekOrigin.Current);
        string value = BinaryText.ReadAscii(reader, length);
        reader.BaseStream.Position = pos;
        return value;
    }

    public static bool MatchesBytesAt(this BinaryReader reader, long relativePosition, params byte[] expected)
    {
        long pos = reader.BaseStream.Position;
        try
        {
            reader.BaseStream.Seek(relativePosition, SeekOrigin.Current);
            if (!reader.HasBytes(expected.Length))
                return false;

            for (int i = 0; i < expected.Length; i++)
            {
                if (reader.ReadByte() != expected[i])
                    return false;
            }

            return true;
        }
        finally
        {
            reader.BaseStream.Position = pos;
        }
    }

    public static byte[] ReadBytesExact(this BinaryReader reader, int count)
    {
        if (count < 0)
            throw new InvalidDataException($"Cannot read a negative byte count: {count}");
        if (!reader.HasBytes(count))
            throw new EndOfStreamException($"Needed {count} bytes, got {reader.BaseStream.Length - reader.BaseStream.Position} remaining.");

        byte[] data = reader.ReadBytes(count);
        if (data.Length != count)
            throw new EndOfStreamException($"Needed {count} bytes, got {data.Length}.");
        return data;
    }

    public static RentedBuffer ReadRentedBytesExact(this BinaryReader reader, int count)
    {
        if (count < 0)
            throw new InvalidDataException($"Cannot read a negative byte count: {count}");
        if (!reader.HasBytes(count))
            throw new EndOfStreamException($"Needed {count} bytes, got {reader.BaseStream.Length - reader.BaseStream.Position} remaining.");

        RentedBuffer data = RentedBuffer.Rent(count);
        try
        {
            reader.BaseStream.ReadExactly(data.Buffer, 0, count);
            return data;
        }
        catch
        {
            data.Dispose();
            throw;
        }
    }

    public static void SkipBytesExact(this BinaryReader reader, int count)
    {
        if (count < 0)
            throw new InvalidDataException($"Cannot skip a negative byte count: {count}");
        if (!reader.HasBytes(count))
            throw new EndOfStreamException($"Needed to skip {count} bytes, got {reader.BaseStream.Length - reader.BaseStream.Position} remaining.");

        reader.BaseStream.Seek(count, SeekOrigin.Current);
    }
}

internal static class BinaryText
{
    public static string ReadAscii(BinaryReader reader, int length = -1)
    {
        if (length >= 0)
            return Encoding.ASCII.GetString(reader.ReadBytesExact(length)).TrimEnd('\0');

        StringBuilder sb = new();
        int value;
        while ((value = reader.BaseStream.ReadByte()) > 0)
        {
            sb.Append((char)value);
        }

        return sb.ToString();
    }

    public static string ReadAsciiZ(BinaryReader reader, int maxBytes)
    {
        if (maxBytes < 0)
            throw new ArgumentOutOfRangeException(nameof(maxBytes));

        StringBuilder sb = new();
        for (int i = 0; i < maxBytes; i++)
        {
            int value = reader.BaseStream.ReadByte();
            if (value < 0)
                return sb.ToString();
            if (value == 0)
                return sb.ToString();

            sb.Append((char)value);
        }

        if (reader.BaseStream.Position < reader.BaseStream.Length)
            throw new InvalidDataException($"ASCII string exceeds {maxBytes} bytes.");

        return sb.ToString();
    }

    public static byte[] ReadBytesZ(BinaryReader reader, int maxBytes)
    {
        if (maxBytes < 0)
            throw new ArgumentOutOfRangeException(nameof(maxBytes));

        using MemoryStream ms = new();
        for (int i = 0; i < maxBytes; i++)
        {
            int value = reader.BaseStream.ReadByte();
            if (value < 0)
                return ms.ToArray();
            if (value == 0)
                return ms.ToArray();

            ms.WriteByte((byte)value);
        }

        if (reader.BaseStream.Position < reader.BaseStream.Length)
            throw new InvalidDataException($"Null-terminated byte string exceeds {maxBytes} bytes.");

        return ms.ToArray();
    }

    public static string ReadAsciiStop(BinaryReader reader, int length)
    {
        long start = reader.BaseStream.Position;
        StringBuilder sb = new();
        for (int i = 0; i < length && reader.HasBytes(1); i++)
        {
            byte b = reader.ReadByte();
            if (b == 0)
                break;
            sb.Append((char)b);
        }

        reader.BaseStream.Position = start + length;
        return sb.ToString();
    }

    public static string ReadUtf16(BinaryReader reader, int length = -1)
    {
        StringBuilder sb = new();
        if (length >= 0)
        {
            for (int i = 0; i < length && reader.HasBytes(2); i++)
            {
                ushort ch = reader.ReadUInt16();
                if (ch != 0)
                    sb.Append((char)ch);
            }

            return sb.ToString();
        }

        while (reader.HasBytes(2))
        {
            ushort ch = reader.ReadUInt16();
            if (ch == 0)
                break;
            sb.Append((char)ch);
        }

        return sb.ToString();
    }

    public static string ReadUtf16Stop(BinaryReader reader, int length)
    {
        long start = reader.BaseStream.Position;
        StringBuilder sb = new();
        for (int i = 0; i < length && reader.HasBytes(2); i++)
        {
            ushort ch = reader.ReadUInt16();
            if (ch == 0)
                break;
            sb.Append((char)ch);
        }

        reader.BaseStream.Position = start + length * 2L;
        return sb.ToString();
    }

    public static Color ReadColor(BinaryReader reader)
    {
        byte r = reader.ReadByte();
        byte g = reader.ReadByte();
        byte b = reader.ReadByte();
        byte a = reader.ReadByte();
        return Color.FromArgb(a, r, g, b);
    }
}

internal static class BitFlag
{
    public static bool IsSet(byte value, int bit)
    {
        Debug.Assert(bit is >= 0 and < 8);
        return bit is >= 0 and < 8 && (value & (1 << bit)) != 0;
    }

    public static bool IsSet(uint value, int bit)
    {
        Debug.Assert(bit is >= 0 and < 32);
        return bit is >= 0 and < 32 && (value & (1u << bit)) != 0;
    }
}

internal static class Sanitizer
{
    private static readonly char[] InvalidFileNameChars = Path.GetInvalidFileNameChars();
    private static readonly char[] InvalidPathChars = Path.GetInvalidPathChars();

    public static string FileName(string name)
    {
        if (string.IsNullOrWhiteSpace(name))
            return "unnamed";

        char[]? chars = null;
        for (int i = 0; i < name.Length; i++)
        {
            char ch = name[i];
            if (!InvalidFileNameChars.Contains(ch) && !char.IsControl(ch))
                continue;

            chars ??= name.ToCharArray();
            chars[i] = '_';
        }

        string cleaned = chars == null ? name : new string(chars);
        cleaned = cleaned.Trim().TrimEnd('.');
        return string.IsNullOrWhiteSpace(cleaned) ? "unnamed" : cleaned;
    }

    public static string RelativePath(string path)
    {
        string[] parts = path.Replace('/', '\\')
            .Split('\\', StringSplitOptions.RemoveEmptyEntries)
            .Select(FileName)
            .ToArray();

        if (parts.Length == 0)
            return "unnamed";

        string combined = Path.Combine(parts);
        foreach (char ch in InvalidPathChars)
            combined = combined.Replace(ch, '_');
        return combined;
    }
}
