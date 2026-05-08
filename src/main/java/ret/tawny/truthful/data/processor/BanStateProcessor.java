package ret.tawny.truthful.data.processor;

public final class BanStateProcessor {

    private boolean banning = false;
    private long banStartTime = 0;
    private int totalViolations;

    public boolean isBanning() {
        return banning;
    }

    public void setBanning(boolean banning) {
        this.banning = banning;
        if (banning)
            this.banStartTime = System.currentTimeMillis();
        else
            this.banStartTime = 0;
    }

    public long getBanStartTime() {
        return banStartTime;
    }

    public int getVl() {
        return totalViolations;
    }

    public void addVl(int amount) {
        this.totalViolations = Math.max(0, this.totalViolations + amount);
    }

    public void resetTotalViolations() {
        this.totalViolations = 0;
    }
}
