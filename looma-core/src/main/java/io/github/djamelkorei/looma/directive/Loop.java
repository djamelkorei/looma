package io.github.djamelkorei.looma.directive;

/** The {@code loop} variable available inside {@code @foreach}. */
public final class Loop {

    private final int index;
    private final int count;
    private final Loop parent;

    public Loop(int index, int count, Loop parent) {
        this.index = index;
        this.count = count;
        this.parent = parent;
    }

    public int getIndex() { return index; }
    public int getIteration() { return index + 1; }
    public int getCount() { return count; }
    public int getRemaining() { return count - index - 1; }
    public boolean isFirst() { return index == 0; }
    public boolean isLast() { return index == count - 1; }
    public boolean isEven() { return (index + 1) % 2 == 0; }
    public boolean isOdd() { return (index + 1) % 2 == 1; }
    public Loop getParent() { return parent; }
    public int getDepth() { return parent == null ? 1 : parent.getDepth() + 1; }
}
