package downloader;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class StateData
    implements Serializable {

    private HashMap<String, String> converted = new HashMap<>();
    private ArrayList<String> failed = new ArrayList<>();

    public HashMap<String, String> getConverted() {
        return converted;
    }

    public void setConverted(HashMap<String, String> converted) {
        this.converted = converted;
    }

    public ArrayList<String> getFailed() {
        return failed;
    }

    public void setFailed(ArrayList<String> failed) {
        this.failed = failed;
    }
}
