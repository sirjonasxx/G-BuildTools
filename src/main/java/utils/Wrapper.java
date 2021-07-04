package utils;

public class Wrapper<T> {

    private T object;

    public Wrapper(T object) {
        this.object = object;
    }

    public Wrapper() {
        this.object = null;
    }

    public T get() {
        return object;
    }
    public void set(T object) {
        this.object = object;
    }

    public void remove() {
        this.object = null;
    }
    public boolean isPresent() {
        return this.object != null;
    }
}
