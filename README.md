![](https://velog.velcdn.com/images/dodo4723/post/d310aa77-03b5-4fd4-b22c-e696cbed4b33/image.png)

김영한 개발자님의 [실전! 스프링 데이터 JPA](https://www.inflearn.com/course/%EC%8A%A4%ED%94%84%EB%A7%81-%EB%8D%B0%EC%9D%B4%ED%84%B0-JPA-%EC%8B%A4%EC%A0%84/dashboard) 강의를 수강하고 중요한 점이나 인상깊었던 점을 간단히 정리했습니다.

<br>
<br>
<br>
<br>

# 공통 인터페이스 - JpaRepository

`MemberRepository`와 `TeamRepository`등의 `Repository`는 기본 CRUD 기능 구현이 비슷비슷하다.

스프링 데이터 JPA는 이를 묶은 공통 인터페이스 기능을 제공한다.

```java
public interface MemberRepository extends JpaRepository<Member, Long> {}
```

<br>
<br>
<br>
<br>

## 1. 쿼리 메소드 기능
스프링 데이터 JPA가 제공하는 마법 같은 기능

#### 1. 메소드 이름으로 쿼리 생성
- 메소드 이름을 분석해서 JPQL을 생성하고 실행

```java
// 순수 JPA 리포지토리
public List<Member> findByUsernameAndAgeGreaterThan(String username, int age) {
 	return em.createQuery("select m from Member m where m.username = :username and m.age > :age")
 		.setParameter("username", username)
 		.setParameter("age", age)
 		.getResultList();
}

// 스프링 데이터 JPA
public interface MemberRepository extends JpaRepository<Member, Long> {
 	List<Member> findByUsernameAndAgeGreaterThan(String username, int age);
}
```

[필터 조건](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.query-methods.query-creation)과 [쿼리 메소드]( https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.query-methods.query-creation) 기능은 공식 문서 참조

<br>

#### 2. JPA NamedQuery

- 선언한 "도메인 클래스 + .(점) + 메서드 이름"으로 Named 쿼리를 찾아서 실행
- 만약 실행할 Named 쿼리가 없으면 메서드 이름으로 쿼리 생성 전략을 사용
- 참고로 Named 쿼리는 **애플리케이션 실행 시점에 문법 오류를 발견**할 수 있음

```java
@Entity
@NamedQuery(
 	name="Member.findByUsername",
 	query="select m from Member m where m.username = :username")
public class Member {...}

public interface MemberRepository extends JpaRepository<Member, Long> { //** 여기 선언한 Member 도메인 클래스
	
	// @Query(name = "Member.findByUsername") // 생략 가능
 	List<Member> findByUsername(@Param("username") String username);
}
```

<br>

#### 3. `@Query`, 리포지토리 메소드에 쿼리 정의하기

```java
public interface MemberRepository extends JpaRepository<Member, Long> {

	@Query("select m from Member m where m.username= :username and m.age = :age")
	List<Member> findUser(@Param("username") String username, @Param("age") int age);
    
    // DTO로 직접 조회도 가능
    @Query("select new study.datajpa.dto.MemberDto(m.id, m.username, t.name) " +
 		"from Member m join m.team t")
	List<MemberDto> findMemberDto();
}
```

<br>
<br>
<br>
<br>

## 2. 페이징과 정렬

#### 반환 타입
- `org.springframework.data.domain.Page` : 추가 count 쿼리 결과를 포함하는 페이징
- `org.springframework.data.domain.Slice` : 추가 count 쿼리 없이 다음 페이지만 확인 가능 (내부적으로 limit + 1조회 - 최근 모바일 리스트 생각해보면 됨)
- `List (자바 컬렉션)`: 추가 count 쿼리 없이 결과만 반환

```java
public interface MemberRepository extends Repository<Member, Long> {
 	
    Page<Member> findByAge(int age, Pageable pageable);
}

// Test - 나이가 10살, 이름으로 내림차순, 첫번째 페이지, 페이지당 3건
PageRequest pageRequest = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "username"));
Page<Member> page = memberRepository.findByAge(10, pageRequest);

List<Member> content = page.getContent(); //조회된 데이터
assertThat(content.size()).isEqualTo(3); //조회된 데이터 수
assertThat(page.getTotalElements()).isEqualTo(5); //전체 데이터 수
assertThat(page.getNumber()).isEqualTo(0); //페이지 번호
assertThat(page.getTotalPages()).isEqualTo(2); //전체 페이지 번호
assertThat(page.isFirst()).isTrue(); //첫번째 항목인가?
assertThat(page.hasNext()).isTrue(); //다음 페이지가 있는가?
```

<br>
<br>
<br>
<br>

## 3. 벌크성 수정 쿼리

- `@Modifying` 어노테이션을 사용
- 사용 후 영속성 컨텍스트 초기화 권장

```java
@Modifying // (clearAutomatically = true) 영속성 컨텍스트 초기화
@Query("update Member m set m.age = m.age + 1 where m.age >= :age")
int bulkAgePlus(@Param("age") int age);
```

<br>
<br>
<br>
<br>

## 4. @EntityGraph

- 페치 조인(FETCH JOIN)의 간편 버전
- LEFT OUTER JOIN 사용

```java
// 공통 메서드 오버라이드
@Override
@EntityGraph(attributePaths = {"team"})
List<Member> findAll();

// JPQL + 엔티티 그래프
@EntityGraph(attributePaths = {"team"})
@Query("select m from Member m")
List<Member> findMemberEntityGraph();

// 메서드 이름으로 쿼리에서 특히 편리
@EntityGraph(attributePaths = {"team"})
List<Member> findByUsername(String username)


// NamedEntityGraph
@NamedEntityGraph(name = "Member.all", attributeNodes =
@NamedAttributeNode("team"))
@Entity
public class Member {}

@EntityGraph("Member.all")
@Query("select m from Member m")
List<Member> findMemberEntityGraph();

```

<br>
<br>
<br>
<br>

## 5. 사용자 정의 리포지토리 구현
스프링 데이터 JPA가 제공하는 인터페이스를 직접 구현하면 구현해야 하는 기능이 너무 많음

다양한 이유(아래 참고)로 인터페이스의 메서드를 직접 구현하고 싶다면?
>
- JPA 직접 사용(EntityManager)
- 스프링 JDBC Template 사용
- MyBatis 사용
- 데이터베이스 커넥션 직접 사용 등등...
- Querydsl 사용

- 규칙 : `리포지토리 인터페이스 이름 + Impl` 또는 `사용자 정의 인터페이스 명 + Impl`
- 스프링 데이터 JPA가 인식해서 스프링 빈으로 등록

```java
@RequiredArgsConstructor // MemberRepositoryImpl 이름도 가능
public class MemberRepositoryCustomImpl implements MemberRepositoryCustom {
 	private final EntityManager em;
    
 	@Override
 	public List<Member> findMemberCustom() {
 	return em.createQuery("select m from Member m")
 		.getResultList();
 	}
}

// 사용자 정의 인터페이스 상속
public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {}
```

<br>
<br>
<br>
<br>

## 6. Auditing

엔티티를 생성, 변경할 때 변경한 사람과 시간을 추적하고 싶으면? (등록일, 수정일, 등록자, 수정자등)

```java

@EntityListeners(AuditingEntityListener.class) // 엔티티에 해줘야함
@MappedSuperclass
public class BaseTimeEntity {
 	@CreatedDate
 	@Column(updatable = false)
 	private LocalDateTime createdDate;
    
 	@LastModifiedDate
 	private LocalDateTime lastModifiedDate;
}

public class BaseEntity extends BaseTimeEntity {
 	@CreatedBy
 	@Column(updatable = false)
 	private String createdBy;
    
 	@LastModifiedBy
 	private String lastModifiedBy;
}
```
등록자, 수정자를 처리해주는 `AuditorAware` 스프링 빈 등록

실무에서는 세션 정보나, 스프링 시큐리티 로그인 정보에서 ID를 받음
```java
@Bean
public AuditorAware<String> auditorProvider() {
 	return () -> Optional.of(UUID.randomUUID().toString());
}
```

<br>
<br>
<br>
<br>

## 7. Web 확장 - 도메인 클래스 컨버터
HTTP 파라미터로 넘어온 엔티티의 아이디로 엔티티 객체를 찾아서 바인딩

```java
@GetMapping("/members/{id}")
 =public String findMember(@PathVariable("id") Long id) {
 	Member member = memberRepository.findById(id).get();
 	return member.getUsername();
}

//위 코드가 아래 코드로
@GetMapping("/members/{id}")
public String findMember(@PathVariable("id") Member member) {
 	return member.getUsername();
}
```

<br>
<br>
<br>
<br>

## 8. Web 확장 - 페이징과 정렬

스프링 데이터가 제공하는 페이징과 정렬 기능을 스프링 MVC에서 편리하게 사용할 수 있다.

요청 파라미터 예 : `/members?page=0&size=3&sort=id,desc&sort=username,desc`

```java
@GetMapping("/members")
public Page<Member> list(Pageable pageable) {

 	Page<Member> page = memberRepository.findAll(pageable);
 	return page;
}
```

<br>

#### Page 내용을 DTO로 변환하기

`Page`는 `map()` 을 지원해서 내부 데이터를 다른 것으로 변경할 수 있다.

```java
@Data
public class MemberDto {
 	private Long id;
 	private String username;
 
 	public MemberDto(Member m) {
 		this.id = m.getId();
 		this.username = m.getUsername();
 	}
}

@GetMapping("/members") // Page.map() 사용
public Page<MemberDto> list(Pageable pageable) {
 	Page<Member> page = memberRepository.findAll(pageable);
 	Page<MemberDto> pageDto = page.map(MemberDto::new);
 return pageDto;
}
```

<br>
<br>
<br>
<br>

## 9. 새로운 엔티티를 구별하는 법(중요)

JPA 식별자 생성 전략이 `@GenerateValue` 면 `save()` 호출 시점에 식별자가 없으므로 새로운 엔티티로 인식해서 정상 동작한다. 그런데 JPA 식별자 생성 전략이 `@Id` 만 사용해서 직접 할당이면 이미 식별자 값이 있는 상태로 `save()` 를 호출한다. 따라서 이 경우 `merge()` 가 호출된다. 

`merge()` 는 우선 DB를 호출해서 값을 확인하고, DB에 값이 없으면 새로운 엔티티로 인지하므로 매우 비효율 적이다. 따라서 `Persistable` 를 사용해서 새로운 엔티티 확인 여부를 직접 구현하게는 효과적이다.

등록시간(`@CreatedDate`)을 조합해서 사용하면 이 필드로 새로운 엔티티 여부를 편리하게 확인할 수 있다.

```java
public class Item implements Persistable<String> {
 	@Id
 	private String id;
 	@CreatedDate
 	private LocalDateTime createdDate;
 
 // Persistable 인터페이스의 isNew를 오버라이딩하여 판단 로직 변경
 	@Override
 	public boolean isNew() {
 		return createdDate == null;
 	}
```