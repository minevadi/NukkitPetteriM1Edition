package cn.nukkit.network.protocol;

public class ItemComponentPacket extends DataPacket {

    @Override
    public byte pid() {
        return ProtocolInfo.ITEM_COMPONENT_PACKET;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putVarInt(0); // Send empty array for now
    }
}
