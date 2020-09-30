package cn.nukkit.level.format.generic;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.anvil.util.BlockStorage;
import cn.nukkit.level.format.anvil.util.NibbleArray;
import cn.nukkit.nbt.stream.NBTInputStream;
import cn.nukkit.nbt.stream.NBTOutputStream;
import cn.nukkit.nbt.tag.*;
import com.github.luben.zstd.Zstd;
import cn.nukkit.level.format.beacon.AbstractBeaconLoader;
import cn.nukkit.level.format.beacon.Beacon;
import cn.nukkit.level.format.beacon.BeaconChunk;
import cn.nukkit.level.format.beacon.BeaconChunkSection;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class BasicBeaconLoader extends AbstractBeaconLoader {

    public BasicBeaconLoader() {
    }

    public BasicBeaconLoader(File file) throws IOException {
        this(file,true);
    }
    public BasicBeaconLoader(File file, boolean load) throws IOException {
        this.file = file;
        if (load){
            RandomAccessFile rand = new RandomAccessFile(file, "r");
            rand.seek(0);
            try {
                byte[] serializedWorld = new byte[(int) rand.length()];
                rand.readFully(serializedWorld);
                this.fullBytes = serializedWorld;
            } finally {
                rand.close();
            }
        }
    }

    public BasicBeaconLoader(byte[] bytes) {
        this.fullBytes = bytes;
    }

    @Override
    public int floor(double d) {
        final int floor = (int) d;
        return floor == d ? floor : floor - (int) (Double.doubleToRawLongBits(d) >>> 63);
    }

    @Override
    public void saveToFile(Beacon provider) throws IOException {
        this.provider = provider;
        RandomAccessFile rand = new RandomAccessFile(file, "rw");
        rand.seek(0);
        rand.setLength(0);
        rand.write(serialize(provider));
        rand.close();
    }

    @Override//OKUMA
    public Beacon deserialize(Beacon provider) {
        this.provider = provider;
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(fullBytes));

        try {
            String worldName = provider.getConstructingName();
            byte[] header = readHeader(stream);
            if (!Arrays.equals(HEADER, header)) {
                //throw new CorruptedWorldException(worldName);
            }

            version = readVersion(stream);

            minX = stream.readShort();
            minZ = stream.readShort();

            width = stream.readShort();
            depth = stream.readShort();

            if (width <= 0 || depth <= 0) {
                //throw new CorruptedWorldException(worldName);
            }
            
            int bitmaskSize = (int) Math.ceil((width * depth) / 8.0D);
            chunkBitset = readBitset(stream, bitmaskSize);

            LinkedHashMap<Long,BeaconChunk> chunks = readChunks(stream);
            CompoundTag tileEntitiesCompound = readTileEntities(stream);
            CompoundTag entitiesCompound = readEntities(stream);
            CompoundTag levelDataCompound = readLevelData(stream);


            if (levelDataCompound == null) levelDataCompound = new CompoundTag("");

            for (BeaconChunk chunk : chunks.values()) {
                provider.setChunk(chunk.getX(),chunk.getZ(),chunk);
            }
            levelDataCompound.putString("levelName",worldName);
            provider.levelData = levelDataCompound;

            if (entitiesCompound != null) {
                CompoundTag tag = entitiesCompound.getCompound("entities");

                for (Tag xtag : tag.getAllTags()) {
                    CompoundTag entityCompound = (CompoundTag) xtag;
                    ListTag<DoubleTag> listTag = entityCompound.getList("Pos", DoubleTag.class);

                    int chunkX = floor(listTag.get(0).data) >> 4;
                    int chunkZ = floor(listTag.get(2).data) >> 4;
                    long chunkKey = ((long) chunkZ) * Integer.MAX_VALUE + ((long) chunkX);
                    BeaconChunk chunk = chunks.get(chunkKey);

                    if (chunk == null) {
                       // throw new CorruptedWorldException(worldName);
                    }

                    Entity.createEntity(entityCompound.getString("id"), chunk, entityCompound);
                }
            }
            if (tileEntitiesCompound != null) {
                CompoundTag tag = tileEntitiesCompound.getCompound("tiles");

                for (Tag xtag : tag.getAllTags()) {
                    CompoundTag tileEntityCompound = (CompoundTag) xtag;
                    int chunkX = ((IntTag) tileEntityCompound.get("x")).data >> 4;
                    int chunkZ = ((IntTag) tileEntityCompound.get("z")).data >> 4;
                    long chunkKey = ((long) chunkZ) * Integer.MAX_VALUE + ((long) chunkX);
                    BeaconChunk chunk = chunks.get(chunkKey);

                    if (chunk == null) {
                       // throw new CorruptedWorldException(worldName);
                    }

                    BlockEntity.createBlockEntity(tileEntityCompound.getString("id"), chunk, tileEntityCompound);
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;

    }

    @Override//YAZMA
    public byte[] serialize(Beacon provider) {
        this.provider = provider;
        List<BeaconChunk> sortedChunks;

        synchronized (provider.chunks) {
            sortedChunks = new ArrayList<>();
            provider.chunks.values().forEach(fullChunk -> {
                if (fullChunk instanceof BeaconChunk){
                    sortedChunks.add((BeaconChunk) fullChunk);
                }
            });
        }

        sortedChunks.removeIf(chunk -> chunk == null || Arrays.stream(chunk.getSections()).allMatch(s -> {
            if (s == null) {
                return true;
            }
            for (byte b : s.getIdArray()) {
                if (b != 0) {
                    return false;
                }
            }
            return true;
        }));
        sortedChunks.sort(Comparator.comparingLong(chunk -> (long) chunk.getZ() * Integer.MAX_VALUE + (long) chunk.getX()));

        minX = sortedChunks.stream().mapToInt(BeaconChunk::getX).min().orElse(0);
        minZ = sortedChunks.stream().mapToInt(BeaconChunk::getZ).min().orElse(0);
        int maxX = sortedChunks.stream().mapToInt(BeaconChunk::getX).max().orElse(0);
        int maxZ = sortedChunks.stream().mapToInt(BeaconChunk::getZ).max().orElse(0);
        width = (short) (maxX - minX + 1);
        depth = (short) (maxZ - minZ + 1);

        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(byteStream);

            writeHeader(stream);
            writeVersion(stream);

            stream.writeShort(minX);
            stream.writeShort(minZ);

            stream.writeShort(width);
            stream.writeShort(depth);
            
            chunkBitset = new BitSet(width * depth);

            for (BeaconChunk chunk : sortedChunks) {
                int index = (chunk.getZ() - minZ) * width + (chunk.getX() - minX);
                chunkBitset.set(index, true);
            }
            int chunkMaskSize = (int) Math.ceil((width * depth) / 8.0D);
            writeBitset(stream,chunkBitset,chunkMaskSize);

            writeChunks(stream,sortedChunks);

            List<CompoundTag> tileEntitiesList = new ArrayList<>();
            sortedChunks.forEach(chunk -> chunk.getBlockEntities().values().forEach(blockEntity -> {
                blockEntity.saveNBT();
                tileEntitiesList.add(blockEntity.namedTag);
            }));

            List<CompoundTag> entitiesList = new ArrayList<>();
            sortedChunks.forEach(chunk -> chunk.getEntities().values().forEach(entity -> {
                entity.saveNBT();
                entitiesList.add(entity.namedTag);
            }));

            writeTileEntities(stream,tileEntitiesList);
            writeEntities(stream,entitiesList);

            writeLevelData(stream, provider.levelData.clone());
            return byteStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    @Override
    public byte[] readHeader(DataInputStream stream) throws IOException {
        byte[] header = new byte[HEADER.length];
        stream.read(header);
        return header;
    }

    @Override
    public void writeHeader(DataOutputStream stream) throws IOException {
        stream.write(HEADER);
    }

    @Override
    public byte readVersion(DataInputStream stream) throws Exception {
        return stream.readByte();
    }

    @Override
    public void writeVersion(DataOutputStream stream) throws Exception {
        stream.write(VERSION);
    }

    @Override
    public CompoundTag deserializeCompoundTag(byte[] bytes) throws Exception {
        if (bytes.length == 0) {
            return null;
        }

        NBTInputStream stream = new NBTInputStream(new ByteArrayInputStream(bytes));
        return (CompoundTag) Tag.readNamedTag(stream);
    }

    @Override
    public byte[] serializeCompoundTag(CompoundTag tag) throws Exception {
        if (tag == null || tag.getAllTags().isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        NBTOutputStream stream = new NBTOutputStream(byteStream);
        Tag.writeNamedTag(tag,stream);

        return byteStream.toByteArray();
    }
    
    @Override
    public void writeBitset(DataOutputStream stream, BitSet set, int fixedSize) throws Exception {
        byte[] array = set.toByteArray();
        stream.write(array);

        int chunkMaskPadding = fixedSize - array.length;

        for (int i = 0; i < chunkMaskPadding; i++) {
            stream.write(0);
        }
    }

    @Override
    public BitSet readBitset(DataInputStream stream, int bitmaskSize) throws Exception {
        byte[] chunkBitmask = new byte[bitmaskSize];
        stream.read(chunkBitmask);
        return BitSet.valueOf(chunkBitmask);
    }

    @Override
    public LinkedHashMap<Long, BeaconChunk> readChunks(DataInputStream stream) throws Exception {
        int compressedChunkDataSize = stream.readInt();
        int uncompressedChunkDataSize = stream.readInt();

        byte[] compressedChunks = new byte[compressedChunkDataSize];
        byte[] uncompressedChunk = new byte[uncompressedChunkDataSize];
        stream.read(compressedChunks);
        Zstd.decompress(uncompressedChunk, compressedChunks);

        ByteArrayInputStream byteStream = new ByteArrayInputStream(uncompressedChunk);
        DataInputStream stream1 = new DataInputStream(byteStream);
        LinkedHashMap<Long, BeaconChunk> chunks = new LinkedHashMap<>();

        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int bitsetIndex = z * width + x;

                if (chunkBitset.get(bitsetIndex)) {
                    BeaconChunk chunk = readChunk(stream1,x,z);

                    chunks.put(((long) minZ + z) * Integer.MAX_VALUE + ((long) minX + x), chunk);
                }
            }
        }

        return chunks;
    }

    @Override
    public void writeChunks(DataOutputStream stream, List<BeaconChunk> list) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream stream1 = new DataOutputStream(byteStream);
        for (BeaconChunk chunk : list) {
            writeChunk(stream1,chunk);
        }
        byte[] uncompressedChunks = byteStream.toByteArray();
        byte[] compressedChunks = Zstd.compress(uncompressedChunks);
        int compressedSize = compressedChunks.length;
        int uncompressedSize = uncompressedChunks.length;

        stream.writeInt(compressedSize);
        stream.writeInt(uncompressedSize);
        stream.write(compressedChunks);
    }

    @Override
    public short[] byteToShortArray(byte[] bytes) throws Exception {
        short[] shorts = new short[bytes.length / 2];

        int ia = 0;
        for (int i = 0; i < bytes.length; i += 2) {
            byte[] byt = new byte[]{bytes[i], bytes[i + 1]};
            shorts[ia] = byteToShort(byt);
            ia++;
        }
        return shorts;
    }

    @Override
    public byte[] shortToByteArray(short[] shortes) throws Exception {
        byte[] bytes = new byte[shortes.length * 2];

        int ia = 0;
        for (short value : shortes) {
            byte[] c = shortToByte(value);
            bytes[ia] = c[0];
            bytes[ia + 1] = c[1];
            ia += 2;
        }
        return bytes;
    }

    @Override
    public byte[] shortToByte(short shrt) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(shrt);
        return buffer.array();
    }

    @Override
    public short byteToShort(byte[] bytes) throws Exception {
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(bytes[0]);
        bb.put(bytes[1]);
        return bb.getShort(0);
    }

    @Override
    public void writeChunk(DataOutputStream stream, BeaconChunk chunk) throws Exception {
        byte[] biomes = chunk.getBiomeIdArray();
        for (int i = 0; i < 256; i++) {
            stream.writeByte(biomes[i]);
        }
        byte[] heightMap = chunk.getHeightMapArray();

        for (int i = 0; i < 256; i++) {
            stream.writeByte(heightMap[i]);
        }
        
        long inhabitedTime = chunk.getInhabitedTime();
        boolean terrainGenerated = chunk.isGenerated();
        boolean terrainPopulated = chunk.isPopulated();
        
        stream.writeLong(inhabitedTime);
        stream.writeBoolean(terrainGenerated);
        stream.writeBoolean(terrainPopulated);
        
        ChunkSection[] sections = chunk.getSections();
        BitSet sectionBitmask = new BitSet(16);

        for (int i = 0; i < sections.length; i++) {
            sectionBitmask.set(i, sections[i] != null && sections[i] instanceof BeaconChunkSection);
        }

        writeBitset(stream, sectionBitmask,2);

        for (ChunkSection as : sections) {
            if (as instanceof BeaconChunkSection){
                writeSection(stream, (BeaconChunkSection) as);
            }
        }
    }

    @Override
    public BeaconChunk readChunk(DataInputStream stream, int x, int z) throws Exception {
        byte[] byteBiomes = new byte[256];
        stream.read(byteBiomes);

        byte[] heightMap = new byte[256];
        stream.read(heightMap);

        long inhabitedTime = stream.readLong();
        boolean terrainGenerated = stream.readBoolean();
        boolean terrainPopulated = stream.readBoolean();
        
        BitSet sectionBitset = readBitset(stream,2);
        BeaconChunkSection[] chunkSectionArray = new BeaconChunkSection[16];

        for (int i = 0; i < 16; i++) {
            if (sectionBitset.get(i)) {
                chunkSectionArray[i] = readSection(stream,i);
            } else {
                chunkSectionArray[i] = new BeaconChunkSection(i);
            }
        }
        return new BeaconChunk(provider, minX + x, minZ + z,
                chunkSectionArray, heightMap, byteBiomes, new ArrayList<>(), new ArrayList<>(),
                inhabitedTime, terrainGenerated, terrainPopulated);
    }

    @Override
    public void writeSection(DataOutputStream stream, BeaconChunkSection section) throws Exception {
        boolean hasBlockLight = section.getLightArray() != null && section.getLightArray().length != 0;
        stream.writeBoolean(hasBlockLight);

        if (hasBlockLight) {
            stream.write(section.getLightArray());
        }

        boolean hasSkyLight = section.getSkyLightArray() != null && section.getSkyLightArray().length != 0;
        stream.writeBoolean(hasSkyLight);

        if (hasSkyLight) {
            stream.write(section.getSkyLightArray());
        }
        
        section.getStorage().getBlockData();
        
        stream.write(section.getStorage().getBlockIds());
        stream.write(section.getStorage().getBlockData());
    }

    @Override
    public BeaconChunkSection readSection(DataInputStream stream, int y) throws Exception {
        byte[] blockLightArray = new byte[2048];

        if (stream.readBoolean()) {
            stream.read(blockLightArray);
        } else {
            blockLightArray = null;
        }

        byte[] skyLightArray = new byte[2048];

        if (stream.readBoolean()) {
            stream.read(skyLightArray);
        } else {
            skyLightArray = null;
        }
        
        byte[] blockIdsArray = new byte[BlockStorage.SECTION_SIZE];
        stream.read(blockIdsArray);

        byte[] blockDataArray = new byte[BlockStorage.SECTION_SIZE >> 1];
        stream.read(blockDataArray);

        NibbleArray nibbleBlockDataArray = new NibbleArray(blockDataArray);

        BlockStorage storage = new BlockStorage(blockIdsArray, nibbleBlockDataArray);

        return new BeaconChunkSection(y, storage, blockLightArray, skyLightArray);
    }

    @Override
    public CompoundTag readEntities(DataInputStream stream) throws Exception {
        boolean hasEntities = stream.readBoolean();
        if (hasEntities){
            int compressedEntitiesSize = stream.readInt();
            int uncompressedEntitiesSize = stream.readInt();

            byte[] compressedEntities = new byte[compressedEntitiesSize];
            byte[] uncompressedEntities = new byte[uncompressedEntitiesSize];

            stream.read(compressedEntities);
            Zstd.decompress(uncompressedEntities, compressedEntities);

            return deserializeCompoundTag(uncompressedEntities);
        }
        return null;
    }

    @Override
    public void writeEntities(DataOutputStream stream, List<CompoundTag> list) throws Exception {
        stream.writeBoolean(!list.isEmpty());
        if (!list.isEmpty()){
            CompoundTag entitiesCompound = new CompoundTag("");
            entitiesCompound.putCompound("entities", new CompoundTag());

            int i = 0;
            for (CompoundTag tag : list) {
                entitiesCompound.getCompound("entities").putCompound(String.valueOf(i), tag);
                i++;
            }

            byte[] uncompressedEntities = serializeCompoundTag(entitiesCompound);
            byte[] compressedEntities = Zstd.compress(uncompressedEntities);

            stream.writeInt(compressedEntities.length);
            stream.writeInt(uncompressedEntities.length);
            stream.write(compressedEntities);
        }
    }


    @Override
    public CompoundTag readLevelData(DataInputStream stream) throws Exception {
        int compressedLevelDataSize = stream.readInt();
        int uncompressedLevelDataSize = stream.readInt();
        byte[] compressedLevelData = new byte[compressedLevelDataSize];
        byte[] uncompressedLevelData = new byte[uncompressedLevelDataSize];

        stream.read(compressedLevelData);

        Zstd.decompress(uncompressedLevelData,compressedLevelData);

        return deserializeCompoundTag(uncompressedLevelData);
    }

    @Override
    public void writeLevelData(DataOutputStream stream, CompoundTag tag) throws Exception {
        byte[] uncompressedLevelData = serializeCompoundTag(tag != null ? tag : new CompoundTag());
        byte[] compressedLevelData = Zstd.compress(uncompressedLevelData);
        int uncompressedLevelDataSize = uncompressedLevelData.length;
        int compressedLevelDataSize = compressedLevelData.length;

        stream.writeInt(compressedLevelDataSize);
        stream.writeInt(uncompressedLevelDataSize);
        stream.write(compressedLevelData);
    }

    @Override
    public CompoundTag readTileEntities(DataInputStream stream) throws Exception {
        boolean hasTiles = stream.readBoolean();
        if (hasTiles){
            int compressedTilesSize = stream.readInt();
            int uncompressedTilesSize = stream.readInt();

            byte[] compressedTiles = new byte[compressedTilesSize];
            byte[] uncompressedTiles = new byte[uncompressedTilesSize];

            stream.read(compressedTiles);

            Zstd.decompress(uncompressedTiles, compressedTiles);

            return deserializeCompoundTag(uncompressedTiles);
        }
        return null;
    }

    @Override
    public void writeTileEntities(DataOutputStream stream, List<CompoundTag> list) throws Exception {
        stream.writeBoolean(!list.isEmpty());
        if (!list.isEmpty()) {
            CompoundTag tilesCompound = new CompoundTag("");
            tilesCompound.putCompound("tiles", new CompoundTag());

            int i = 0;
            for (CompoundTag tag : list) {
                tilesCompound.getCompound("tiles").putCompound(String.valueOf(i), tag);
                i++;
            }

            byte[] uncompressedTiles = serializeCompoundTag(tilesCompound);
            byte[] compressedTiles = Zstd.compress(uncompressedTiles);

            stream.writeInt(compressedTiles.length);
            stream.writeInt(uncompressedTiles.length);
            stream.write(compressedTiles);
        }
    }
    
}
