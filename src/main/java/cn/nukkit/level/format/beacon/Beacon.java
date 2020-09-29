package cn.nukkit.level.format.beacon;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.nukkit.level.format.generic.BasicBeaconLoader;
import cn.nukkit.event.level.BeaconLevelSaveRequestEvent;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.BaseLevelProvider;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ChunkException;
import cn.nukkit.utils.ThreadCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public class Beacon extends BaseLevelProvider{

        static private final byte[] PAD_256 = new byte[256];
	
    protected String _constructingName;
    public CompoundTag levelData;
    protected String fileId;
    
	public Beacon(String path, String name, CompoundTag levelData) throws Exception {
        super(null, path, name, true);
        this._constructingName = name;
		this.levelData = levelData;
	}
	
    public Beacon(Level level, String path, String name) throws Exception {
        super(level, path, name, true);
        this._constructingName = name;
        File file = new File(path + "/" + name + ".beacon");
        if (file.exists()) {
        	AbstractBeaconLoader converter = new BasicBeaconLoader(file);
            converter.deserialize(this);
        }
    }

    public Beacon(Level level, String path, String name, final byte[] serializedWorld) throws Exception {
        super(level, path, name, true);
        this._constructingName = name;
        this.fileId = fileId;
        AbstractBeaconLoader converter = new BasicBeaconLoader(serializedWorld);
        converter.deserialize(this);
    }
    
    public String getConstructingName() {
        return _constructingName;
    }
	
    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public static String getProviderName() {
        return "beacon";
    }

    public static byte getProviderOrder() {
        return ORDER_YZX;
    }

    public static boolean usesChunkSection() {
        return true;
    }
    
    public static boolean isValid(final String path) {
        final File worldNameDir = new File(path);
        if (!worldNameDir.exists()) {
            return false;
        }
        final File[] files = worldNameDir.listFiles();
        if (files == null) {
            return false;
        }
        for (final File file : files) {
            if (file.getName().endsWith(".beacon")) {
                return true;
            }
        }
        return false;
    }
    
    public static Beacon generate(String path, String name, long seed, Class<? extends Generator> generator) throws Exception {
        return generate(path, name, seed, generator, new HashMap<>());
    }

    public static Beacon generate(String path, String name, long seed, Class<? extends Generator> generator, Map<String, String> options) throws Exception {
        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File file = new File(path + "/" + name + ".beacon");
        if (!file.exists()) {
            file.createNewFile();
        }

        CompoundTag levelData = new CompoundTag("Data")
                .putCompound("GameRules", new CompoundTag())
                .putLong("DayTime", 0)
                .putInt("GameType", 0)
                .putString("generatorName", Generator.getGeneratorName(generator))
                .putString("generatorOptions", options.getOrDefault("preset", ""))
                .putInt("generatorVersion", 1)
                .putBoolean("hardcore", false)
                .putBoolean("initialized", true)
                .putLong("LastPlayed", System.currentTimeMillis() / 1000)
                .putString("LevelName", name)
                .putBoolean("raining", false)
                .putInt("rainTime", 0)
                .putLong("RandomSeed", seed)
                .putInt("SpawnX", 128)
                .putInt("SpawnY", 70)
                .putInt("SpawnZ", 128)
                .putBoolean("thundering", false)
                .putInt("thunderTime", 0)
                .putLong("Time", 0);

        Beacon format = new Beacon(path, name, levelData);
        format.saveLevelData();
        return format;
    }
    
    @Override
    public void saveLevelData() {
        try {
            if (getPath() == null) {
                BeaconLevelSaveRequestEvent requestEvent = new BeaconLevelSaveRequestEvent(this);
                Server.getInstance().getPluginManager().callEvent(requestEvent);
                return;
            }
            File file = new File(getPath() + "/" + _constructingName + ".beacon");
            AbstractBeaconLoader converter = new BasicBeaconLoader(file, false);
            converter.saveToFile(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public BeaconChunk getEmptyChunk(int chunkX, int chunkZ) {
        return BeaconChunk.getEmptyChunk(chunkX, chunkZ, this);
    }
    
    @Override
    public AsyncTask requestChunkTask(int protocol, int x, int z) throws ChunkException {
    	BeaconChunk chunk = (BeaconChunk) this.getChunk(x, z, false);
        if (chunk == null) {
            throw new ChunkException("Invalid Chunk Set");
        }

        long timestamp = chunk.getChanges();

        byte[] blockEntities = new byte[0];

        if (!chunk.getBlockEntities().isEmpty()) {
            List<CompoundTag> tagList = new ArrayList<>();

            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof BlockEntitySpawnable) {
                    tagList.add(((BlockEntitySpawnable) blockEntity).getSpawnCompound());
                }
            }

            try {
                blockEntities = NBTIO.write(tagList, ByteOrder.LITTLE_ENDIAN, true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Map<Integer, Integer> extra = chunk.getBlockExtraDataArray();
        BinaryStream extraData;
        if (!extra.isEmpty()) {
            extraData = new BinaryStream();
            extraData.putVarInt(extra.size());
            for (Map.Entry<Integer, Integer> entry : extra.entrySet()) {
                extraData.putVarInt(entry.getKey());
                extraData.putLShort(entry.getValue());
            }
        } else {
            extraData = null;
        }

        BinaryStream stream = ThreadCache.binaryStream.get().reset();
        int subChunkCount = 0;
        cn.nukkit.level.format.ChunkSection[] sections = chunk.getSections();
        for (int i = sections.length - 1; i >= 0; i--) {
            if (!sections[i].isEmpty()) {
                subChunkCount = i + 1;
                break;
            }
        }
        if (protocol < ProtocolInfo.v1_12_0) {
            stream.putByte((byte) subChunkCount);
        }
        for (int i = 0; i < subChunkCount; i++) {
            if (protocol < ProtocolInfo.v1_13_0) {
                stream.putByte((byte) 0);
                stream.put(sections[i].getBytes());
            } else {
                sections[i].writeTo(protocol, stream);
            }
        }
        if (protocol < ProtocolInfo.v1_12_0) {
            for (byte height : chunk.getHeightMapArray()) {
                stream.putByte(height);
            }
            stream.put(PAD_256);
        }
        stream.put(chunk.getBiomeIdArray());
        stream.putByte((byte) 0);
        if (extraData != null) {
            stream.put(extraData.getBuffer());
        } else {
            stream.putVarInt(0);
        }
        stream.put(blockEntities);

        this.getLevel().chunkRequestCallback(protocol, timestamp, x, z, subChunkCount, stream.getBuffer());

        return null;
    }
    
    private int lastPosition = 0;

    @Override
    public void doGarbageCollection(long time) {
        long start = System.currentTimeMillis();
        int maxIterations = size();
        if (lastPosition > maxIterations) lastPosition = 0;
        int i;
        synchronized (chunks) {
            ObjectIterator<BaseFullChunk> iter = chunks.values().iterator();
            if (lastPosition != 0) iter.skip(lastPosition);
            for (i = 0; i < maxIterations; i++) {
                if (!iter.hasNext()) {
                    iter = chunks.values().iterator();
                }
                if (!iter.hasNext()) break;
                BaseFullChunk chunk = iter.next();
                if (chunk == null) continue;
                if (chunk.isGenerated() && chunk.isPopulated() && chunk instanceof BeaconChunk) {
                    chunk.compress();
                    if (System.currentTimeMillis() - start >= time) break;
                }
            }
        }
        lastPosition += i;
    }
    
    @Override
    public synchronized BaseFullChunk loadChunk(long index, int chunkX, int chunkZ, boolean create) {
        if (this.level.timings.syncChunkLoadDataTimer != null) this.level.timings.syncChunkLoadDataTimer.startTiming();
        BaseFullChunk chunk = null;
        try {
            synchronized (this.chunks) {
                if (this.chunks.containsKey(index)) {
                	chunk = this.chunks.get(index);
                }
            }
        }catch (Exception e) {
            throw new RuntimeException(e);
		}
        if (chunk == null) {
            if (create) {
                chunk = this.getEmptyChunk(chunkX, chunkZ);
                putChunk(index, chunk);
            }
        } else {
            putChunk(index, chunk);
        }
        if (this.level.timings.syncChunkLoadDataTimer != null) this.level.timings.syncChunkLoadDataTimer.stopTiming();
        return chunk;
    }
    
    /*@Override
    public synchronized void saveChunk(int X, int Z) {
        BaseFullChunk chunk = this.getChunk(X, Z);
        if (chunk != null) {
            try {
            	this.saveLevelData();
                //this.loadRegion(X >> 5, Z >> 5).writeChunk(chunk);
            } catch (Exception e) {
                throw new ChunkException("Error saving chunk (" + X + ", " + Z + ')', e);
            }
        }
    }
    
    @Override
    public synchronized void saveChunk(int x, int z, FullChunk chunk) {
        if (!(chunk instanceof BeaconChunk)) {
            throw new ChunkException("Invalid Chunk class");
        }
        try {
            this.saveLevelData();
            this.getRegion(regionX, regionZ).writeChunk(chunk);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }*/
	
    @Override
    public void saveChunks() {
    }
	
    @Override
    public synchronized void saveChunk(int X, int Z) {
    }

    @Override
    public synchronized void saveChunk(int x, int z, FullChunk chunk) {
    }

    @Override
    public void unloadChunks() {
    }

    @Override
    public boolean unloadChunk(int X, int Z) {
        return false;
    }

    @Override
    public boolean unloadChunk(int X, int Z, boolean safe) {
        return false;
    }

    public static BeaconChunkSection createChunkSection(int y) {
    	BeaconChunkSection cs = new BeaconChunkSection(y);
        return cs;
    }
    
    @Override
    public GameRules getGamerules() {
        final GameRules rules = GameRules.getDefault();

        if (this.levelData.contains("gameRules")) {
            rules.readNBT(this.levelData.getCompound("gameRules"));
        }

        return rules;
    }

    @Override
    public String getName() {
        return this.levelData.getString("LevelName");
    }

    @Override
    public void setGameRules(GameRules rules) {
        this.levelData.putCompound("gameRules", rules.writeNBT());
    }

    @Override
    public void updateLevelName(String name) {
        if (!this.getName().equals(name)) {
            this.levelData.putString("LevelName", name);
        }
    }
    
    @Override
    public String getGenerator() {
        return this.levelData.getString("generatorName");
    }

    @Override
    public Map<String, Object> getGeneratorOptions() {
        return new HashMap<String, Object>() {
            {
                put("preset", levelData.getString("generatorOptions"));
            }
        };
    }
    
    @Override
    public boolean isRaining() {
        return this.levelData.getBoolean("raining");
    }

    @Override
    public void setRaining(boolean raining) {
        this.levelData.putBoolean("raining", raining);
    }

    @Override
    public int getRainTime() {
        return this.levelData.getInt("rainTime");
    }

    @Override
    public void setRainTime(int rainTime) {
        this.levelData.putInt("rainTime", rainTime);
    }

    @Override
    public boolean isThundering() {
        return this.levelData.getBoolean("thundering");
    }

    @Override
    public void setThundering(boolean thundering) {
        this.levelData.putBoolean("thundering", thundering);
    }

    @Override
    public int getThunderTime() {
        return this.levelData.getInt("thunderTime");
    }

    @Override
    public void setThunderTime(int thunderTime) {
        this.levelData.putInt("thunderTime", thunderTime);
    }

    @Override
    public long getCurrentTick() {
        return this.levelData.getLong("DayTime");
    }

    @Override
    public void setCurrentTick(long currentTick) {
        this.levelData.putLong("DayTime", currentTick);
    }

    @Override
    public long getTime() {
        return this.levelData.getLong("Time");
    }

    @Override
    public void setTime(long value) {
        this.levelData.putLong("Time", value);
    }

    @Override
    public long getSeed() {
        return this.levelData.getLong("RandomSeed");
    }

    @Override
    public void setSeed(long value) {
        this.levelData.putLong("RandomSeed", value);
    }
    
    @Override
    public Vector3 getSpawn() {
        if (this.spawn == null) {
            return new Vector3(0, 100, 0);
        }
        return spawn;
    }

    @Override
    public void setSpawn(Vector3 pos) {
        this.levelData.putDouble("spawnX", pos.x);
        this.levelData.putDouble("spawnY", pos.y);
        this.levelData.putDouble("spawnZ", pos.z);
        this.spawn = pos;
    }

}
