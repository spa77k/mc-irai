package net.mcirai.contractboard.session;

public class CreateRequestSession {

    public enum Step {
        TITLE,
        DESCRIPTION,
        REWARD,
        EXPIRE,
        MIN_STARS
    }

    private Step step = Step.TITLE;
    private String title;
    private String description;
    private double reward;
    private int expireHours;
    private int minStars;

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getReward() {
        return reward;
    }

    public void setReward(double reward) {
        this.reward = reward;
    }

    public int getExpireHours() {
        return expireHours;
    }

    public void setExpireHours(int expireHours) {
        this.expireHours = expireHours;
    }

    public int getMinStars() {
        return minStars;
    }

    public void setMinStars(int minStars) {
        this.minStars = minStars;
    }
}
