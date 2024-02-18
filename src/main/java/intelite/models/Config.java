package intelite.models;

import org.apache.commons.lang3.math.Fraction;

/**
 *
 * @author JOSE GABRIEL
 */
public class Config {
    private String fto;
    private String custom;
    private Long vbr; // Bitrate video
    private Fraction fps; // Fotogramas por segundo
    
    private Long abr; // Bitrate audio

    public Config() {
        super();
    }

    public Config(String fto, String custom, Long vbr, Fraction fps, Long abr) {
        this.fto = fto;
        this.custom = custom;
        this.vbr = vbr;
        this.fps = fps;
        this.abr = abr;
    }

    public String getFto() {
        return fto;
    }

    public void setFto(String fto) {
        this.fto = fto;
    }

    public Long getVbr() {
        return vbr;
    }

    public void setVbr(Long vbr) {
        this.vbr = vbr;
    }

    public Fraction getFps() {
        return fps;
    }

    public void setFps(Fraction fps) {
        this.fps = fps;
    }

    public Long getAbr() {
        return abr;
    }

    public void setAbr(Long abr) {
        this.abr = abr;
    }

    public String getCustom() {
        return custom;
    }

    public void setCustom(String custom) {
        this.custom = custom;
    }
   
}
