package demo;

import java.util.ArrayList;

/** Demo unit for the Tier-2/Tier-3 gate — engine-owned source, not vendored. */
public class Registry {

    private final ArrayList<String> names = new ArrayList<String>();

    /** Adds a name unless present. */
    public void register(String name) {
        if (!names.contains(name)) {
            names.add(name); // keep insertion order
        }
    }

    public int size() {
        return names.size();
    }

    public ArrayList<String> all() {
        ArrayList<String> copy = new ArrayList<String>();
        copy.addAll(names);
        return copy;
    }
}
