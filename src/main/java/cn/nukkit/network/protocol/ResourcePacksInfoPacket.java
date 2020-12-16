package cn.nukkit.network.protocol;

import cn.nukkit.resourcepacks.ResourcePack;
import lombok.ToString;

@ToString
public class ResourcePacksInfoPacket extends DataPacket {

    public boolean mustAccept;
    public boolean scripting;
    public ResourcePack[] behaviourPackEntries = new ResourcePack[0];
    public ResourcePack[] resourcePackEntries = new ResourcePack[0];

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putBoolean(this.mustAccept);
        if (protocol >= ProtocolInfo.v1_9_0) {
            this.putBoolean(this.scripting);
        }

        encodePacks(this.resourcePackEntries);
        encodePacks(this.behaviourPackEntries);
    }

    private void encodePacks(ResourcePack[] packs) {
        this.putLShort(packs.length);
        for (ResourcePack entry : packs) {
            this.putString(entry.getPackId().toString());
            this.putString(entry.getPackVersion());
            this.putLLong(entry.getPackSize());
            this.putString(""); // encryption key
            this.putString(""); // sub-pack name
            if (protocol > ProtocolInfo.v1_5_0) {
                this.putString(""); // content identity
                if (protocol >= ProtocolInfo.v1_9_0) {
                    this.putBoolean(false); // scripting
                    if (protocol >= ProtocolInfo.v1_16_200) {
                        this.putBoolean(false); // raytracing capable
                    }
                }
            }
        }
    }

    @Override
    public byte pid() {
        return ProtocolInfo.RESOURCE_PACKS_INFO_PACKET;
    }
}