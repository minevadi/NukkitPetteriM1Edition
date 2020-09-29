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
	public byte[] toBinary() {
		return null;
	}
    
}
