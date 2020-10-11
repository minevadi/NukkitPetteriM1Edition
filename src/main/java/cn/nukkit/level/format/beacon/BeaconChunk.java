package cn.nukkit.level.format.beacon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.nbt.tag.CompoundTag;

public class BeaconChunk extends BaseChunk  {
	
    public static final BeaconChunk FULL_BEDROCK_CHUNK;

    static {
        final BeaconChunk chunk = new BeaconChunk(Beacon.class, true);
        for (int Y = 0; Y < 16; Y++) {
            final BeaconChunkSection section = new BeaconChunkSection(Y);
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        section.setBlockId(x, y, z, BlockID.AIR);
                        section.setBlockData(x, y, z, 0);
                        section.setBlockLight(x, y, z, 0);
                        section.setBlockSkyLight(x, y, z, 0);
                    }
                }
            }
            chunk.sections[Y] = section;
        }
        chunk.biomes = new byte[256];
        Arrays.fill(chunk.biomes, (byte) 0);
        chunk.heightMap = new byte[256];
        Arrays.fill(chunk.heightMap, (byte) 256);
        chunk.NBTtiles = new ArrayList<>();
        chunk.NBTentities = new ArrayList<>();
        for (ChunkSection section : chunk.getSections()) {
            ((BeaconChunkSection) section).locked = true;
        }
        FULL_BEDROCK_CHUNK = chunk;
    }
	
    protected long inhabitedTime;
    protected boolean terrainPopulated;
    protected boolean terrainGenerated;
	
    @Override
    public BeaconChunk clone() {
        return (BeaconChunk) super.clone();
    }
    
    public BeaconChunk(LevelProvider level) {
        this(level != null ? level.getClass() : null);
        this.provider = level;
    }
    
    public BeaconChunk(Class<? extends LevelProvider> providerClass) {
        this.providerClass = providerClass;

        this.biomes = new byte[256];
        this.sections = new ChunkSection[16];
        System.arraycopy(EmptyChunkSection.EMPTY, 0, this.sections, 0, 16);
    }
	
    public BeaconChunk(final Class<? extends LevelProvider> providerClass, boolean custom) {
        this.providerClass = providerClass;

        this.biomes = new byte[256];
        this.sections = new ChunkSection[16];
        System.arraycopy(EmptyChunkSection.BEACON_EMPTY, 0, this.sections, 0, 16);
    }
    
    public BeaconChunk(LevelProvider provider, int x, int z, BeaconChunkSection[] sections, byte[] heightMap, byte[] biomes, List<CompoundTag> entities, List<CompoundTag> tiles, long inhabitedTime, boolean terrainPopulated, boolean terrainGenerated) {
        this.provider = provider;
        this.providerClass = provider.getClass();
        this.x = x;
        this.z = z;
        this.sections = sections;
        this.heightMap = heightMap;
        this.biomes = biomes;
        this.NBTentities = entities;
        this.NBTtiles = tiles;
        this.inhabitedTime = inhabitedTime;
        this.terrainPopulated = terrainPopulated;
        this.terrainGenerated = terrainGenerated;
    }

    public static BeaconChunk getEmptyChunk(final int chunkX, final int chunkZ) {
        return BeaconChunk.getEmptyChunk(chunkX, chunkZ, null);
    }

    public static BeaconChunk getEmptyChunk(final int chunkX, final int chunkZ, final LevelProvider provider) {
        try {
            BeaconChunk chunk;
            if (provider != null) {
                chunk = new BeaconChunk(provider);
            } else {
                chunk = new BeaconChunk(Beacon.class);
            }

            chunk.setPosition(chunkX, chunkZ);

            chunk.heightMap = new byte[256];
            chunk.inhabitedTime = 0;
            chunk.terrainGenerated = false;
            chunk.terrainPopulated = false;
            return chunk;
        } catch (final Exception e) {
            return null;
        }
    }
	
    public static BeaconChunk getBorderChunk(final Level level, final int chunkX, final int chunkZ) {
        try {
            return FULL_BEDROCK_CHUNK.copy(level, chunkX, chunkZ);
        } catch (final Exception e) {
            e.printStackTrace();
            return null;
        }
    }
	
    public BeaconChunk copy(Level level, int x, int z) {
        BeaconChunk chunk = (BeaconChunk) super.clone();
        chunk.providerClass = providerClass;
        chunk.x = x;
        chunk.z = z;
        this.provider = level.getProvider();
        this.providerClass = level.getProvider().getClass();
        return chunk;
    }
    
    public long getInhabitedTime() {
		return inhabitedTime;
	}
    
    @Override
    public boolean isPopulated() {
        return this.terrainPopulated;
    }

    @Override
    public void setPopulated() {
        this.setPopulated(true);
    }

    @Override
    public void setPopulated(boolean value) {
        if (value != this.terrainPopulated) {
            this.terrainPopulated = value;
            setChanged();
        }
    }

    @Override
    public boolean isGenerated() {
        return this.terrainGenerated || this.terrainPopulated;
    }

    @Override
    public void setGenerated() {
        this.setGenerated(true);
    }

    @Override
    public void setGenerated(boolean value) {
        if (this.terrainGenerated != value) {
            this.terrainGenerated = value;
            setChanged();
        }
    }
	
    @Override
    public boolean unload() throws Exception {
        return false;
    }

    @Override
    public boolean unload(boolean save) throws Exception {
        return false;
    }

    @Override
    public boolean unload(boolean save, boolean safe) {
        return false;
    }

	@Override
	public byte[] toBinary() {
		return null;
	}
    
}
