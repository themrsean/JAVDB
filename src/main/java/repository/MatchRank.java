package repository;

public enum MatchRank {
    PRIMARY_EXACT(10),
    ALIAS_EXACT(20),
    PRIMARY_PREFIX(30),
    ALIAS_PREFIX(40),
    PRIMARY_SUBSTRING(50),
    ALIAS_SUBSTRING(60);

    private final int orderValue;

    MatchRank(int orderValue) {
        this.orderValue = orderValue;
    }

    public int orderValue() {
        return orderValue;
    }
}
