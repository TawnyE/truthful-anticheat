package ret.tawny.truthful.data.tracker;

import java.util.ArrayList;
import java.util.List;

public class ActionTracker {

    private boolean sprinting;
    private boolean sneaking;
    private boolean digging;
    private long lastDigTime;

    private boolean usingItem;
    private int lastUseItemTick;
    private boolean usingRiptide;
    private int pendingRiptideTicks;
    private int usingItemTicks;

    private boolean inventoryOpen;
    private int inventoryOpenTick;
    private long lastInventoryClose;
    private long lastWindowClick;
    private final List<Long> clickDelays = new ArrayList<>();
    private final List<Integer> clickedSlots = new ArrayList<>();

    private int currentSlot;
    private int lastSlot;

    public void reset() {
        this.sprinting = false;
        this.sneaking = false;
        this.digging = false;
        this.lastDigTime = 0;
        this.usingItem = false;
        this.usingRiptide = false;
        this.pendingRiptideTicks = 0;
        this.usingItemTicks = 0;
        this.inventoryOpen = false;
        this.inventoryOpenTick = 0;
        this.lastWindowClick = 0;
        this.clickDelays.clear();
        this.clickedSlots.clear();
    }

    public boolean isSprinting() { return sprinting; }
    public void setSprinting(boolean b) { this.sprinting = b; }

    public boolean isSneaking() { return sneaking; }
    public void setSneaking(boolean b) { this.sneaking = b; }

    public boolean isDigging() { return digging; }

    public void setDigging(boolean b) {
        this.digging = b;
        if (b) {
            this.lastDigTime = System.currentTimeMillis();
        }
    }

    public boolean isUsingItem() { return usingItem; }

    public void setUsingItem(boolean b, int ticksTracked) {
        this.usingItem = b;
        if (!b) this.lastUseItemTick = ticksTracked;
    }

    public int getLastUseItemTick() { return lastUseItemTick; }
    public boolean isUsingRiptide() { return usingRiptide; }

    public void setUsingRiptide(boolean b) {
        this.usingRiptide = b;
        if (b) this.pendingRiptideTicks = 0;
    }

    public int getPendingRiptideTicks() { return pendingRiptideTicks; }
    public void setPendingRiptideTicks(int t) { this.pendingRiptideTicks = t; }
    public void incrementPendingRiptideTicks() { this.pendingRiptideTicks++; }
    public void resetPendingRiptideTicks() { this.pendingRiptideTicks = 0; }
    public int getUsingItemTicks() { return usingItemTicks; }
    public void setUsingItemTicks(int t) { this.usingItemTicks = t; }
    public void incrementUsingItemTicks() { this.usingItemTicks++; }
    public boolean isInventoryOpen() { return inventoryOpen; }

    public void setInventoryOpen(boolean b, int ticksTracked) {
        this.inventoryOpen = b;
        if (b) {
            this.inventoryOpenTick = ticksTracked;
        } else {
            this.lastInventoryClose = System.currentTimeMillis();
        }
    }

    public int getInventoryOpenTick() { return inventoryOpenTick; }
    public long getLastInventoryClose() { return lastInventoryClose; }
    public long getLastWindowClick() { return lastWindowClick; }
    public void setLastWindowClick(long t) { this.lastWindowClick = t; }
    public List<Long> getClickDelays() { return clickDelays; }
    public List<Integer> getClickedSlots() { return clickedSlots; }
    public int getCurrentSlot() { return currentSlot; }
    public void setCurrentSlot(int s) { this.currentSlot = s; }
    public int getLastSlot() { return lastSlot; }
    public void setLastSlot(int s) { this.lastSlot = s; }

    public void handleTick() {
        if (this.usingItem) {
            this.usingItemTicks++;
            if (this.usingItemTicks > 35) {
                this.usingItem = false;
                this.usingItemTicks = 0;
            }
        } else {
            this.usingItemTicks = 0;
        }

        if (this.digging && System.currentTimeMillis() - this.lastDigTime > 1000) {
            this.digging = false;
        }
    }
}