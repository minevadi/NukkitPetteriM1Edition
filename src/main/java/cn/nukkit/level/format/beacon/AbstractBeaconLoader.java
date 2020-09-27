package com.mvadi.skyblock.island.level.beacon;

import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.nbt.tag.CompoundTag;

import java.io.*;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;

public abstract class AbstractBeaconLoader {
	
    public static final byte VERSION = 0x04;
    public static final byte[] HEADER = new byte[]{116, 114, 112};

    protected byte[] fullBytes;
    protected int version;
    protected LevelProvider provider;
    protected int minX;
    protected int minZ;
    protected int width;
    protected int depth;
    protected BitSet chunkBitset;
    protected File file;

    public abstract int floor(double d);
    public abstract void saveToFile(Beacon provider) throws IOException;
    public abstract Beacon deserialize(Beacon provider) throws Exception;
    public abstract byte[] serialize(Beacon provider) throws Exception;
    public abstract byte[] readHeader(DataInputStream stream) throws Exception;
    public abstract void writeHeader(DataOutputStream stream) throws Exception;
    public abstract byte readVersion(DataInputStream stream) throws Exception;
    public abstract void writeVersion(DataOutputStream stream) throws Exception;
    public abstract CompoundTag deserializeCompoundTag(byte[] bytes) throws Exception;
    public abstract byte[] serializeCompoundTag(CompoundTag tag) throws Exception;
    public abstract void writeBitset(DataOutputStream stream, BitSet bitSet, int fixedSize) throws Exception;
    public abstract BitSet readBitset(DataInputStream stream, int bitmaskSize) throws Exception;
    public abstract LinkedHashMap<Long, BeaconChunk> readChunks(DataInputStream stream) throws Exception;
    public abstract void writeChunks(DataOutputStream stream, List<BeaconChunk> list) throws Exception;
    public abstract short[] byteToShortArray(byte[] bytes) throws Exception;
    public abstract byte[] shortToByteArray(short[] shortes) throws Exception;
    public abstract byte[] shortToByte(short shrt) throws Exception;
    public abstract short byteToShort(byte[] bytes) throws Exception;
    public abstract void writeChunk(DataOutputStream stream, BeaconChunk chunk) throws Exception;
    public abstract BeaconChunk readChunk(DataInputStream stream, int x, int z) throws Exception;
    public abstract void writeSection(DataOutputStream stream, BeaconChunkSection section) throws Exception;
    public abstract BeaconChunkSection readSection(DataInputStream stream, int y) throws Exception;

    public abstract CompoundTag readEntities(DataInputStream stream) throws Exception;
    public abstract void writeEntities(DataOutputStream stream, List<CompoundTag> list) throws Exception;
    public abstract CompoundTag readLevelData(DataInputStream stream) throws Exception;
    public abstract void writeLevelData(DataOutputStream stream, CompoundTag tag) throws Exception;
    public abstract CompoundTag readTileEntities(DataInputStream stream) throws Exception;
    public abstract void writeTileEntities(DataOutputStream stream, List<CompoundTag> list) throws Exception;
}