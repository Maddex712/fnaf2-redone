package dev.thor.fnaf2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Small, offline reader for the verified Steam FNaF 2 executable only.
 * No game code is executed and no third-party extractor is bundled.
 * Offsets and record layouts are documented in STEAM-FORMAT.md.
 */
final class SteamImporter {
    static final String SHA256="84db9972c70ae02ba9596e1be624f2942545ee939611e7fcd593b75a67d41ac6";
    interface Progress { void update(String message); }
    static void extract(File exe,File destination,Progress progress)throws Exception {
        progress.update("Verifying your Steam executable…");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(exe))){
            byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);
        }
        StringBuilder hash=new StringBuilder();for(byte v:digest.digest())hash.append(String.format(Locale.US,"%02x",v&255));
        if(!SHA256.equals(hash.toString()))throw new IOException("Unsupported game file. Select the original Steam FNaF 2 FiveNightsatFreddys2.exe (not FNaF 1 or a shortcut).");
        File images=new File(destination,"Images"),sounds=new File(destination,"Sounds");
        if(!images.mkdirs()||!sounds.mkdirs())throw new IOException("Cannot create game folders");
        try(RandomAccessFile input=new RandomAccessFile(exe,"r")){
            input.seek(2617442L);
            if(le32(input)!=804)throw new IOException("Unexpected image count");
            HashSet<Integer> ids=new HashSet<>();
            for(int i=0;i<804;i++){
                int id=le32(input)-1, raw=le32(input),packed=le32(input);
                if(id<0||id>4095||!ids.add(id))throw new IOException("Invalid image handle");
                byte[] data=inflate(input,raw,packed);
                writeImage(data,new File(images,String.format(Locale.US,"%05d.png",id)));
                progress.update("Preparing images • "+(i+1)+" / 804");
            }
            if(input.getFilePointer()!=189032448L)throw new IOException("Image bank did not end correctly");
            input.seek(189036292L);
            if(le32(input)!=66)throw new IOException("Unexpected sound count");
            HashSet<String> names=new HashSet<>();
            for(int i=0;i<66;i++){
                le32(input);le32(input);le32(input);
                int raw=le32(input),flags=le32(input);le32(input);
                int nameLength=le32(input),packed=le32(input);
                if(flags!=1||nameLength<1||nameLength>100)throw new IOException("Unexpected sound record");
                byte[] data=inflate(input,raw,packed);int offset=nameLength*2;
                if(offset+12>data.length)throw new IOException("Truncated sound");
                String name=new String(data,0,offset,StandardCharsets.UTF_16LE);
                int end=name.indexOf('\0');if(end>=0)name=name.substring(0,end);
                if(!name.matches("[a-zA-Z0-9 _-]{1,100}")||!names.add(name))throw new IOException("Invalid sound name");
                if(!"RIFF".equals(new String(data,offset,4,StandardCharsets.US_ASCII)))throw new IOException("Invalid WAV data");
                try(OutputStream out=new FileOutputStream(new File(sounds,name+".wav"))){out.write(data,offset,data.length-offset);}
                progress.update("Preparing sounds • "+(i+1)+" / 66");
            }
            if(input.getFilePointer()!=217749307L)throw new IOException("Sound bank did not end correctly");
        }
        String manifest="{\"format\":\"fnaf2-steam-288\",\"sourceSha256\":\""+SHA256+"\",\"images\":804,\"sounds\":66}";
        try(OutputStream out=new FileOutputStream(new File(destination,"pack.json"))){out.write(manifest.getBytes(StandardCharsets.UTF_8));}
    }
    static int le32(RandomAccessFile in)throws IOException {return Integer.reverseBytes(in.readInt());}
    static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
    static int i32(byte[] b,int p){return u16(b,p)|(u16(b,p+2)<<16);}
    static byte[] inflate(RandomAccessFile input,int raw,int packed)throws IOException {
        if(raw<32||raw>64*1024*1024||packed<1||packed>64*1024*1024||packed>input.length()-input.getFilePointer())throw new IOException("Invalid compressed record size");
        byte[] compressed=new byte[packed];input.readFully(compressed);byte[] data=new byte[raw];
        try(InputStream inflater=new InflaterInputStream(new ByteArrayInputStream(compressed))){
            int p=0,n;while(p<raw&&(n=inflater.read(data,p,raw-p))!=-1)p+=n;
            if(p!=raw||inflater.read()!=-1)throw new IOException("Incomplete compressed record");
        }return data;
    }
    static void writeImage(byte[] data,File file)throws IOException {
        if(data.length<32)throw new IOException("Truncated image header");
        int w=u16(data,12),h=u16(data,14),flags=data[17]&255;
        if(w<1||h<1||w>4096||h>4096||data[16]!=4||(flags!=0&&flags!=16))throw new IOException("Unsupported image format");
        int colorStride=(w+(w&1))*3,alphaStride=(w+3)&~3;
        int alphaStart=32+colorStride*h,expected=alphaStart+((flags&16)!=0?alphaStride*h:0);
        if(data.length!=expected||i32(data,8)!=data.length-32)throw new IOException("Invalid image layout");
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(DeflaterOutputStream z=new DeflaterOutputStream(compressed)){
            byte[] row=new byte[w*4+1];
            for(int y=0;y<h;y++){
                for(int x=0;x<w;x++){
                    int src=32+y*colorStride+x*3,dst=1+x*4;
                    row[dst]=data[src+2];row[dst+1]=data[src+1];row[dst+2]=data[src];
                    row[dst+3]=(flags&16)!=0?data[alphaStart+y*alphaStride+x]:
                        (byte)(row[dst]==data[28]&&row[dst+1]==data[29]&&row[dst+2]==data[30]?0:255);
                }z.write(row);
            }
        }
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))){
            out.writeLong(0x89504e470d0a1a0aL);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream header=new DataOutputStream(bytes);
            header.writeInt(w);header.writeInt(h);header.write(new byte[]{8,6,0,0,0});
            chunk(out,"IHDR",bytes.toByteArray());chunk(out,"IDAT",compressed.toByteArray());chunk(out,"IEND",new byte[0]);
        }
    }
    static void chunk(DataOutputStream out,String name,byte[] data)throws IOException {
        byte[] tag=name.getBytes(StandardCharsets.US_ASCII);CRC32 crc=new CRC32();crc.update(tag);crc.update(data);
        out.writeInt(data.length);out.write(tag);out.write(data);out.writeInt((int)crc.getValue());
    }
}
