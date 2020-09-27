package cn.nukkit.level.format.beacon;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.nukkit.level.format.generic.BasicBeaconLoader;

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
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ChunkException;
import cn.nukkit.utils.ThreadCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

public class Beacon extends BaseLevelProvider{

    public CompoundTag levelData;
    protected String _constructingName;
	
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
        AbstractBeaconLoader converter = new BasicBeaconLoader(serializedWorld);
        converter.deserialize(this);
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
    
    public static boolean isValid(String path) {
        File worldNameDir = new File(path);
        if (!worldNameDir.exists()) {
            return false;
        }
        File[] files = worldNameDir.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (file.getName().endsWith(".beacon")) {
                return true;
            }
        }
        return false;
    }
    
    public static Beacon generate(String path, String name, long seed, Class<? extends Generator> generator) throws Exception {
        return generate(path, name, seed, generator, new HashMap<>());
    }

    public static Beacon generate(final String path,final String name, final long seed, Cfinal lass<? extends Generator> generator, final Map<String, String> options) throws Exception {
        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File file = new File(path + "/" + name + ".beacon");
        if (!file.exists()) {
            file.createNewFile();
        }

        CompoundTag levelData = new CompoundTag("Data")
                .putCompound("gameRules", new CompoundTag())
                .putLong("dayTime", 0)
                .putInt("gameType", 0)
                .putBoolean("hardcore", false)
                .putBoolean("initialized", true)
                .putLong("lastPlayed", System.currentTimeMillis() / 1000)
                .putString("levelName", name)
                .putBoolean("raining", false)
                .putInt("rainTime", 0)
                .putLong("seed", seed)
                .putDouble("spawnX", 0)
                .putDouble("spawnY", 100)
                .putDouble("spawnZ", 0)
                .putBoolean("thundering", false)
                .putInt("thunderTime", 0)
                .putLong("time", 0);

        Beacon format = new Beacon(path, name, levelData);
        format.saveLevelData();
        return format;
    }
	
    public CompoundTag getLevelData() {
		return levelData;
	}
    
	public void setLevelData(CompoundTag levelData) {
		this.levelData = levelData;
	}
    
    public void saveLevelData() {
        try {
            final File file = new File(getPath() + "/" + _constructingName + ".beacon");
            AbstractBeaconLoader converter = new BasicBeaconLoader(file, false);
            converter.saveToFile(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static BeaconChunkSection createChunkSection(int y) {
        return new BeaconChunkSection(y);
    }

    public String getConstructingName() {
        return _constructingName;
    }

    public void setConstructingName(String _constructingName) {
        this._constructingName = _constructingName;
    }
    
	@Override
	public BaseFullChunk getEmptyChunk(int chunkX, int chunkZ) {
        return BeaconChunk.getEmptyChunk(chunkX, chunkZ, this);
	}

	@Override
	public AsyncTask requestChunkTask(int protocol, int x, int z) {
		BeaconChunk chunk = (BeaconChunk)this.getChunk(x, z, false);
        if (chunk == null) {
            throw new ChunkException("Invalid Chunk sent");
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
            	blockEntities = NBTIO.write(tagList, ByteOrder.LITTLE_ENDIAN);
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
        int subChunkCount  = 0;
        cn.nukkit.level.format.ChunkSection[] sections = chunk.getSections();
        for (int i = sections.length - 1; i >= 0; i--) {
            if (!sections[i].isEmpty()) {
            	subChunkCount  = i + 1;
                break;
            }
        }
        for (int i = 0; i < subChunkCount; i++) {
            stream.put(sections[i].getBytes());
        }
        stream.put(chunk.getBiomeIdArray());
        stream.putByte((byte) 0);
        if (extraData != null) {
            stream.put(extraData.getBuffer());
        } else {
            stream.putVarInt(0);
        }
        stream.put(blockEntities);

        this.getLevel().chunkRequestCallback(protocol, timestamp, x, z, subChunkCount , stream.getBuffer());

        return null;
	}
	
	public Long2ObjectMap<BaseFullChunk> getChunks() {
		return this.chunks;
	}

	@Override
	public void doGarbageCollection(long time) {
	}

	@Override
	public void doGarbageCollection() {
	}

	@Override
	public boolean loadChunk(int chunkX, int chunkZ, boolean create) {
		long index = Level.chunkHash(chunkX, chunkZ);
		synchronized (this.chunks) {
			if (this.chunks.containsKey(index)) {
				return true;
			}
		}
		return this.loadChunk(index, chunkX, chunkZ, create) != null;
	}

	@Override
	public BaseFullChunk getLoadedChunk(int chunkX, int chunkZ) {
		BaseFullChunk tmp = this.lastChunk.get();
		if (tmp != null && tmp.getX() == chunkX && tmp.getZ() == chunkZ) {
			return tmp;
		}
		final long index = Level.chunkHash(chunkX, chunkZ);
		synchronized (this.chunks) {
			this.lastChunk.set(tmp = this.chunks.get(index));
		}
		return tmp;
	}

	@Override
	public BaseFullChunk getLoadedChunk(long hash) {
		cn.nukkit.level.format.Chunk.Entry entry = Level.getChunkXZ(hash);
		if (entry.chunkX > 4 || entry.chunkX < -5 || entry.chunkZ > 4 || entry.chunkZ < -5) {
			return BeaconChunk.getBorderChunk(this.level, entry.chunkX, entry.chunkZ);
		}

		BaseFullChunk tmp = this.lastChunk.get();
		if (tmp != null && tmp.getIndex() == hash) {
			return tmp;
		}
		synchronized (this.chunks) {
			this.lastChunk.set(tmp = this.chunks.get(hash));
		}
		return tmp;
	}

	@Override
	public BaseFullChunk getChunk(int x, int z, boolean create) {
		if (x > 4 || x < -5 || z > 4 || z < -5) {
			return BeaconChunk.getBorderChunk(this.level, x, z);
		}

		BaseFullChunk tmp = this.lastChunk.get();
		if (tmp != null && tmp.getX() == x && tmp.getZ() == z) {
			return tmp;
		}
		final long index = Level.chunkHash(x, z);
		synchronized (this.chunks) {
			this.lastChunk.set(tmp = this.chunks.get(index));
		}
		if (tmp != null) {
			return tmp;
		} else {
			if (create) {
				tmp = getEmptyChunk(x, z);
				this.putChunk(index, tmp);
				return tmp;
			}
			return null;
		}
	}

	@Override
	public BaseFullChunk loadChunk(long index, int chunkX, int chunkZ, boolean create) {
		return create ? getChunk(chunkX, chunkZ, true) : null;
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
        return this.levelData.getString("levelName");
    }

    @Override
    public void setGameRules(GameRules rules) {
        this.levelData.putCompound("gameRules", rules.writeNBT());
    }

    @Override
    public void updateLevelName(String name) {
        if (!this.getName().equals(name)) {
            this.levelData.putString("levelName", name);
        }
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
        return this.levelData.getLong("time");
    }

    @Override
    public void setCurrentTick(long currentTick) {
        this.levelData.putLong("time", currentTick);
    }

    @Override
    public long getTime() {
        return this.levelData.getLong("dayTime");
    }

    @Override
    public void setTime(long value) {
        this.levelData.putLong("dayTime", value);
    }

    @Override
    public long getSeed() {
        return this.levelData.getLong("seed");
    }

    @Override
    public void setSeed(long value) {
        this.levelData.putLong("seed", value);
    }

    @Override
    public void saveChunks() {
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

    @Override
    public void saveChunk(int X, int Z) {
        // TODO Auto-generated method stub

    }

    @Override
    public void saveChunk(int X, int Z, FullChunk chunk) {
        // TODO Auto-generated method stub

    }

}
