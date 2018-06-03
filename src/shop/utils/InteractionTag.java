package shop.utils;

public enum InteractionTag {
    OPEN_CHECKOUT,
    CLOSE_CHECKOUT,
    START_SERVICE,
    END_SERVICE,
    CHOOSE_QUEUE;

    @Override
    public String toString() {
        return super.toString() + "_";
    }
}
