package cn.nukkit.level.format.beacon;

import java.util.Arrays;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.anvil.util.BlockStorage;
import cn.nukkit.level.format.anvil.util.NibbleArray;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.utils.BinaryStream;

public class BeaconChunkSection implements ChunkSection {

    private static final PalettedBlockStorage EMPTY_STORAGE = new PalettedBlockStorage();

    private final int y;

    private final BlockStorage storage;

    protected byte[] blockLight;
    protected byte[] skyLight;

    boolean locked = false;

    public BeaconChunkSection(int y, BlockStorage storage, byte[] blockLight, byte[] skyLight) {
        this.y = y;
        
        this.storage = storage;
        this.blokLight = blockLight;
        this.skyLight = skyLight;
    }

    public BeaconChunkSection(int y) {
        this.y = y;
        storage = new BlockStorage();
    }

    private static int getWoolIndex(int x, int y, int z) {
        return (y << 8) + (z << 4) + x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getBlockId(int x, int y, int z) {
        synchronized (storage) {
            return storage.getBlockId(x, y, z);
        }
    }

    @Override
    public void setBlockId(int x, int y, int z, int id) {
        synchronized (storage) {
            storage.setBlockId(x, y, z, id);
        }
    }

    @Override
    public int getBlockData(int x, int y, int z) {
        synchronized (storage) {
            return storage.getBlockData(x, y, z);
        }
    }

    @Override
    public void setBlockData(int x, int y, int z, int data) {
        synchronized (storage) {
            storage.setBlockData(x, y, z, data);
        }
    }

    @Override
    public int getFullBlock(int x, int y, int z) {
        synchronized (storage) {
            return storage.getFullBlock(x, y, z);
        }
    }

    @Override
    public Block getAndSetBlock(int x, int y, int z, Block block) {
        synchronized (storage) {
            int fullId = storage.getAndSetFullBlock(x, y, z, block.getFullId());
            return Block.fullList[fullId].clone();
        }
    }

    @Override
    public boolean setFullBlockId(int x, int y, int z, int fullId) {
        synchronized (storage) {
            storage.setFullBlock(x, y, z, (char) fullId);
        }
        return true;
    }

    @Override
    public boolean setBlock(int x, int y, int z, int blockId) {
        synchronized (storage) {
            return setBlock(x, y, z, blockId, 0);
        }
    }

    @Override
    public boolean setBlock(int x, int y, int z, int blockId, int meta) {
          int newFullId = (blockId << 4) + meta;
        synchronized (storage) {
            int previousFullId = storage.getAndSetFullBlock(x, y, z, newFullId);
            return (newFullId != previousFullId);
        }
    }

    @Override
    public int getBlockSkyLight(int x, int y, int z) {
        if (this.skyLight == null) {
            return 0;
        }
        this.skyLight = getSkyLightArray();
        int sl = this.skyLight[(y << 7) | (z << 3) | (x >> 1)] & 0xff;
        if ((x & 1) == 0) {
            return sl & 0x0f;
        }
        return sl >> 4;
    }

    @Override
    public void setBlockSkyLight(int x, int y, int z, int level) {
        if (locked) return;

        if (this.skyLight == null) {
            if (level == 0) return;
            else {
                this.skyLight = new byte[2048];
                Arrays.fill(this.skyLight, (byte) 0xFF);
            }
        }
        int i = (y << 7) | (z << 3) | (x >> 1);
        int old = this.skyLight[i] & 0xff;
        if ((x & 1) == 0) {
            this.skyLight[i] = (byte) ((old & 0xf0) | (level & 0x0f));
        } else {
            this.skyLight[i] = (byte) (((level & 0x0f) << 4) | (old & 0x0f));
        }
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        if (blockLight == null) return 0;
        this.blockLight = getLightArray();
        int l = blockLight[(y << 7) | (z << 3) | (x >> 1)] & 0xff;
        if ((x & 1) == 0) {
            return l & 0x0f;
        }
        return l >> 4;
    }

    @Override
    public void setBlockLight(int x, int y, int z, int level) {
        if (locked) return;

        if (this.blockLight == null) {
            if (level == 0) {
                return;
            } else {
                this.blockLight = new byte[2048];
            }
        }
        int i = (y << 7) | (z << 3) | (x >> 1);
        int old = this.blockLight[i] & 0xff;
        if ((x & 1) == 0) {
            this.blockLight[i] = (byte) ((old & 0xf0) | (level & 0x0f));
        } else {
            this.blockLight[i] = (byte) (((level & 0x0f) << 4) | (old & 0x0f));
        }
    }

    @Override
    public byte[] getIdArray() {
        synchronized (storage) {
            byte[] anvil = new byte[4096];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        int index = getWoolIndex(x, y, z);
                        anvil[index] = (byte) storage.getBlockId(x, y, z);
                    }
                }
            }
            return anvil;
        }
    }

    @Override
    public byte[] getDataArray() {
        synchronized (storage) {
            NibbleArray anvil = new NibbleArray(4096);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        int index = getWoolIndex(x, y, z);
                        anvil.set(index, (byte) storage.getBlockData(x, y, z));
                    }
                }
            }
            return anvil.getData();
        }
    }

    @Override
    public byte[] getSkyLightArray() {
        if (this.skyLight != null) return skyLight;
        return EmptyChunkSection.EMPTY_LIGHT_ARR;
    }

    @Override
    public byte[] getLightArray() {
        if (this.blockLight != null) return blockLight;
        return EmptyChunkSection.EMPTY_LIGHT_ARR;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public byte[] getBytes() {
        synchronized (storage) {
            byte[] ids = storage.getBlockIds();
            byte[] data = storage.getBlockData();
            byte[] merged = new byte[ids.length + data.length];
            System.arraycopy(ids, 0, merged, 0, ids.length);
            System.arraycopy(data, 0, merged, ids.length, data.length);
            return merged;
        }
    }

    @Override
    public void writeTo(int protocol, BinaryStream stream) {
        synchronized (storage) {
            stream.putByte((byte) 8); // Paletted chunk because Mojang messed up the old one
            stream.putByte((byte) 2);
            this.storage.writeTo(protocol, stream);
            EMPTY_STORAGE.writeTo(protocol, stream);
        }
    }

    @Override
    public ChunkSection copy() {
        return new BeaconChunkSection(
                this.y,
                this.storage.copy(),
                this.blockLight == null ? null : this.blockLight.clone(),
                this.skyLight == null ? null : this.skyLight.clone()
        );
    }

}
