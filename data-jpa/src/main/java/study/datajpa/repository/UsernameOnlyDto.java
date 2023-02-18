package study.datajpa.repository;

public class UsernameOnlyDto {

    private final String username;

    public UsernameOnlyDto(String username) { // 파라미터 명을 가지고 분석
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
