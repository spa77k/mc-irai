package net.mcirai.contractboard.session;

public class CreateRequestSession {

    public enum Step {
        TITLE,
        DESCRIPTION,
        REWARD,
        EXPIRE,
        /** チャット入力を終え、確認画面で最低星数・アイテム納品を選んで作成を確定する段階。 */
        CONFIRM
    }

    private Step step = Step.TITLE;
    private String title;
    private String description;
    private double reward;
    private int expireHours;
    private int minStars;
    private boolean itemDelivery;

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    /** 確認画面の段階ではチャット入力を受け取らない(全体チャットを飲み込まない)。 */
    public boolean isAwaitingConfirm() {
        return step == Step.CONFIRM;
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

    public boolean isItemDelivery() {
        return itemDelivery;
    }

    public void setItemDelivery(boolean itemDelivery) {
        this.itemDelivery = itemDelivery;
    }
}
