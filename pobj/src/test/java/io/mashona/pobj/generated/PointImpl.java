package io.mashona.pobj.generated;

import io.mashona.pobj.runtime.MemoryOperations;
import io.mashona.pobj.runtime.MemoryBackedObject;

// automatically generated class.
public class PointImpl implements MemoryBackedObject {

    protected static final int X_OFFSET = 0;
    protected static final int X_STORESIZE = 4;
    protected static final int Y_OFFSET = 4;
    protected static final int Y_STORESIZE = 4;
    protected static final int TOTAL_STORESIZE = 8;

    protected MemoryOperations memory;

    @Override
    public int size() {
        return TOTAL_STORESIZE;
    }

    @Override
    public void setMemory(MemoryOperations memory) {
        this.memory = memory;
    }

    @Override
    public MemoryOperations getMemory() {
        return memory;
    }

    public int getX() {
        return memory.getInt(X_OFFSET);
    }

    public void setX(int x) {
        memory.setInt(X_OFFSET, x);
    }

    public int getY() {
        return memory.getInt(Y_OFFSET);
    }

    public void setY(int y) {
        memory.setInt(Y_OFFSET, y);
    }

}
