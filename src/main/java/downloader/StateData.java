package downloader;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class StateData
        implements Serializable {

    private HashMap<String, String> converted = new HashMap<>();
    private ArrayList<String> failed = new ArrayList<>();
    private HashMap<String, String> urlFileHashes = new HashMap<>();
    private ArrayList<Integer> errCodesImages = new ArrayList<>();

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

    public HashMap<String, String> getUrlFileHashes() {
        return urlFileHashes;
    }

    public void setUrlFileHashes(HashMap<String, String> urlFileHashes) {
        this.urlFileHashes = urlFileHashes;
    }

    public ArrayList<Integer> getErrCodesImages() {
        return errCodesImages;
    }

    public void setErrCodesImages(ArrayList<Integer> errCodesImages) {
        this.errCodesImages = errCodesImages;
    }
}
