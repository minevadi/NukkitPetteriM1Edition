package cn.nukkit.event.level;

import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.server.ServerEvent;
import cn.nukkit.level.format.beacon.Beacon;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class BeaconLevelSaveRequestEvent extends ServerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    protected Beacon provied;

    public BeaconLevelSaveRequestEvent(Beacon provied) {
        this.provied = provied;
    }

    public Beacon getProvied() {
        return provied;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}