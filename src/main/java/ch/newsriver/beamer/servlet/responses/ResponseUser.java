package ch.newsriver.beamer.servlet.responses;

import ch.newsriver.data.user.User;

/**
 * Created by eliapalme on 17/07/16.
 */
public class ResponseUser extends ResponseBase{

    private User user;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

}
