package shop.utils;

public enum FederateTag {
    CLIENT,
    MANAGER,
    STATISTIC,
    QUEUE,
    CHECKOUT;

    @Override
    public String toString() {
        return super.toString() + "_";
    }
}
