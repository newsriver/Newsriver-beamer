package ch.newsriver.beamer.servlet.responses;

/**
 * Created by eliapalme on 16/07/16.
 */
public class ResponseToken extends ResponseBase{

    private  String token;


    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
