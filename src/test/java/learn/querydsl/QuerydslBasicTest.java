package learn.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import learn.querydsl.dto.MemberDto;
import learn.querydsl.dto.QMemberDto;
import learn.querydsl.entity.Member;
import learn.querydsl.entity.QMember;
import learn.querydsl.entity.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import java.util.List;

import static learn.querydsl.entity.QMember.member;
import static learn.querydsl.entity.QTeam.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    @PersistenceUnit
    EntityManagerFactory emf;

    JPAQueryFactory queryFactory;

    @BeforeEach
    void beforeEach() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    void jpql() {
        Member findMember = em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void querydsl() {
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void searchConditions() {
        List<Member> findMembers = queryFactory
                .selectFrom(member) // select member from member -> selectFrom()으로 단축
                .where(
                        member.username.startsWith("mem"),
                        member.age.between(10, 30)
                ) // 여러개의 조건식을 and(), or()로 하나의 메서드 체인으로 엮어도 되고, 쉼표로 구분해서 별개의 파라미터처럼 넣어도 됨 (varargs 지원)
                .fetch();

        assertThat(findMembers.size()).isEqualTo(3);
    }

    @Test
    void resultFetch() {
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch(); // 결과 없으면 빈 리스트 반환

        Member fetchOne = queryFactory
                .selectFrom(member)
                .where(member.age.eq(10))
                .fetchOne(); // 결과 없으면 null 반환, 결과가 둘 이상이면 NonUniqueResultException 발생

        Member fetchFirst = queryFactory
                .selectFrom(member)
                .where(member.age.loe(40))
                .fetchFirst(); // limit(1).fetchOne()과 동일

        // fetchCount(), fetchResults()는 deprecated (예정)

        Long count = queryFactory
                .select(member.count()) // "select count(member.id) ..." 쿼리와 동일
                .from(member)
                .fetchOne();

        assertThat(fetch.size()).isEqualTo(4);
        assertThat(fetchOne.getAge()).isEqualTo(10);
        assertThat(fetchFirst.getAge()).isEqualTo(10);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100)) // age = 100 인 멤버들을
                .orderBy(member.age.desc(), member.username.asc().nullsLast()) // 나이 내림차순 정렬, 이름 오름차순 정렬(이름이 null이면 맨 마지막에 배치)
                .fetch();

        assertThat(result.get(0).getUsername()).isEqualTo("member5");
        assertThat(result.get(1).getUsername()).isEqualTo("member6");
        assertThat(result.get(2).getUsername()).isNull();
    }

    @Test
    void paging() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) // 1개 건너뛰고
                .limit(2) // 2개 조회
                .fetch();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).getUsername()).isEqualTo("member3");
    }

    @Test
    void aggregation() {
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();
        /*
         Tuple은 값들의 집합을 나타내는 타입이라고 보면 됨 (여러 타입의 값들을 조회해서 묶는 경우 사용)
         실무에서는 Tuple 보다는 DTO 타입으로 받아오는 경우가 대부분
         */

        Tuple tuple = result.get(0);

        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    @Test
    void group() {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamATuple = result.get(0);
        Tuple teamBTuple = result.get(1);

        assertThat(teamATuple.get(team.name)).isEqualTo("teamA");
        assertThat(teamATuple.get(member.age.avg())).isEqualTo(15);

        assertThat(teamBTuple.get(team.name)).isEqualTo("teamB");
        assertThat(teamBTuple.get(member.age.avg())).isEqualTo(35);
    }

    @Test
    void join() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team) // (조인 대상, 별칭으로 사용할 Q타입)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    @Test
    void join_on() {
        // on() 활용1: 조인 대상 필터링
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                .on(team.name.eq("teamA"))
                .fetch();
        /*
        내부 조인인 경우 on절은 where절로 필터링 하는 것과 기능이 동일함
        따라서 내부 조인이면 그냥 where를 사용하고, 외부 조인이고 필요한 경우에만 on을 사용하면 됨
         */

        assertThat(result.get(0).get(team).getName()).isEqualTo("teamA");
        assertThat(result.get(2).get(team)).isNull();
    }

    @Test
    void join_on_no_relationship() {
        // on() 활용2: 연관 관계 없는 엔티티 외부 조인 (여기서는 Member와 Team이 연관 관계가 없다고 가정)
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team) // 연관관계가 없다고 가정하기 때문에 member.team이 아니고 생으로 team 외부 조인
                .on(member.username.eq(team.name)) // SQL에서도 연관관계가 없으면 조인 대상과 id 매칭 없이 on절 조건만으로 필터링
                .fetch();

        assertThat(result.get(0).get(team)).isNull();
        assertThat(result.get(4).get(team).getName()).isEqualTo("teamA");
    }

    @Test
    void fetch_join() {
        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin() // 지연 로딩 설정된 Team을 fetch join으로 함께 조회
                .where(member.username.eq("member1"))
                .fetchOne();

        // 영속성 컨텍스트에 올라온 엔티티인지 확인 (Team)
        boolean isLoaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(isLoaded).isTrue();
    }

    @Test
    void subQuery_where() {
        // 서브 쿼리에 사용하는 엔티티의 별칭은 from 절에서 사용한 별칭과 달라야함
        QMember memberSub = new QMember("memberSub");

        // 서브 쿼리를 생성할 때는 JPAExpressions 사용
        JPQLQuery<Integer> subQuery = JPAExpressions.select(memberSub.age.max()).from(memberSub);

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(subQuery))
                .fetch();

        assertThat(result)
                .extracting("age")
                .containsExactly(40);
    }

    @Test
    void subQuery_select() {
        QMember memberSub = new QMember("memberSub");

        JPQLQuery<Double> subQuery = JPAExpressions.select(memberSub.age.avg().subtract(member.age)).from(memberSub);

        // JPA 표준 스펙으로는 select 절에 서브쿼리를 사용할 수 없으나, Hibernate에서 지원함
        List<Tuple> result = queryFactory
                .select(member.username, subQuery)
                .from(member)
                .fetch();

        assertThat(result.get(0).get(1, Double.class)).isEqualTo(15);
    }

    @Test
    void subQuery_from() {
        /*
        from절의 서브쿼리는 SQL에서는 가능하지만, JPQL에서는 불가능하므로 QueryDSL에서도 역시 사용 불가.

        아래의 방식들로 풀어낼 수 있음
        1. 서브 쿼리를 JOIN으로 변경
        2. 쿼리를 분리해서 실행
        3. 네이티브 SQL 사용
         */
    }

    @Test
    void case_simple() {
        /*
        case문을 사용해야하는 경우도 분명히 존재함
        그러나 쿼리에서 case문을 사용하는 어지간한 작업은 애플리케이션 코드 레벨에서 해결하는 것이 나음
        DB에서는 데이터를 가져오는 것에 주력하고, 데이터를 가공하거나 데이터에 로직을 태우는 것은 애플리케이션 안에서 하자
         */
        List<String> result = queryFactory
                .select(
                        member.age
                                .when(10).then("ten")
                                .when(20).then("twenty")
                                .otherwise("etc")
                )
                .from(member)
                .fetch();

        assertThat(result.get(0)).isEqualTo("ten");
        assertThat(result.get(3)).isEqualTo("etc");
    }

    @Test
    void case_complex() {
        // 복잡한 case 처리는 CaseBuilder를 사용
        StringExpression complexCase = new CaseBuilder()
                .when(member.age.between(0, 20)).then("1순위")
                .when(member.age.between(21, 30)).then("2순위")
                .otherwise("3순위");

        List<String> result = queryFactory
                .select(complexCase)
                .from(member)
                .fetch();

        assertThat(result.get(0)).isEqualTo("1순위");
        assertThat(result.get(3)).isEqualTo("3순위");
    }

    @Test
    void constant() {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        assertThat(result.get(0).get(1, String.class)).isEqualTo("A");
    }

    @Test
    void concatenation() {
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .fetch();
        // age는 문자가 아니라서 stringValue()로 문자로 변환해주었는데, 이 메서드는 ENUM을 처리할 때도 요긴하게 사용함

        assertThat(result.get(0)).isEqualTo("member1_10");
    }

    @Test
    void projection_dto_setter() {
        /*
         setter를 통해서 값이 들어감
         필드명이 다르다면 별칭으로 맞춰주면 됨
         */
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class, member.username, member.age))
                .from(member)
                .fetch();

        assertThat(result).extracting("age").containsExactly(10, 20, 30, 40);
    }

    @Test
    void projection_dto_field() {
        /*
         필드 액세스로 값이 들어감 (setter 없이도 동작)
         필드가 private이어도 리플렉션을 통해서 가능
         필드명이 다르다면 별칭으로 맞춰주면 됨
         */
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class, member.username, member.age))
                .from(member)
                .fetch();

        assertThat(result).extracting("age").containsExactly(10, 20, 30, 40);
    }

    @Test
    void projection_dto_constructor() {
        /*
         생성자를 통해서 값이 들어감
         파라미터 타입 및 순서 일치해야함 (필드명은 달라도 상관 없음)
         */
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class, member.username, member.age))
                .from(member)
                .fetch();

        assertThat(result).extracting("age").containsExactly(10, 20, 30, 40);
    }

    @Test
    void projection_dto_queryprojection() {
        /*
        프로젝션 대상인 MemberDto 클래스의 생성자에 @QueryProjection을 붙이면, QueryDSL이 해당 클래스에 대해서도 Q타입을 생성함
        생성된 QMemberDto의 생성자를 호출해서 프로젝션을 수행할 수 있음

        Projections.constructor()와의 차이점:
        - constructor()는 파라미터를 잘못 넘기거나, 필요보다 많이 혹은 적게 넘기는 등의 실수를 해도 컴파일 에러를 못띄우고 런타임 에러를 발생시킴
        - @QueryProjection 방식은 같은 경우에 대해서 컴파일 에러를 발생시켜서 안전함

        단점:
        - 추가적인 Q 클래스를 생성해야함
        - DTO 클래스가 QueryDSL에 의존성을 갖게됨 (생성자에 어노테이션을 붙여야 하므로)
          다양한 레이어에서 참조할텐데 특정 기술에 종속적인 것은 설계 상 좋지 않음
         */
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        assertThat(result).extracting("age").containsExactly(10, 20, 30, 40);
    }

    @Test
    void dynamic_query_booleanbuilder() {
        List<Member> result = searchMember("member1", 10);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).getUsername()).isEqualTo("member1");
        assertThat(result.get(0).getAge()).isEqualTo(10);
    }

    private List<Member> searchMember(String cond1, Integer cond2) {
        BooleanBuilder builder = new BooleanBuilder();

        if (cond1 != null) { // 유저명 조건이 존재하면
            builder.and(member.username.eq(cond1)); // builder에 조건절 추가
        }

        if (cond2 != null) { // 나이 조건이 존재하면
            builder.and(member.age.eq(cond2)); // builder에 조건절 추가
        }

        return queryFactory
                .selectFrom(member)
                .where(builder) // BooleanBuilder로 동적 생성한 조건절 활용
                .fetch();
    }

    @Test
    void dynamic_query_where_param() {
        /*
        장점:
        - 재사용 가능 (다른 쿼리의 조건절에도 필요 시 사용 가능)
        - 조립 가능 (usernameEq()과 ageEq()를 and()로 합쳐서 반환하는 메서드 등을 만들 수 있음)
        - 가독성 향상 (개별 조건절을 메서드로 뽑고, 메서드에 이름을 지어주기 때문에 무슨 조건절인지 파악 가능)
         */
        List<Member> result = searchMember2("member1", 10);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).getUsername()).isEqualTo("member1");
        assertThat(result.get(0).getAge()).isEqualTo(10);
    }

    private List<Member> searchMember2(String username, Integer age) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(username), ageEq(age)) // 제시된 조건 중에 null은 무시됨
                .fetch();
    }

    private BooleanExpression usernameEq(String username) {
        return username != null ? member.username.eq(username) : null;
    }

    private BooleanExpression ageEq(Integer age) {
        return age != null ? member.age.eq(age) : null;
    }

    @Test
    void bulk_update() {
        /*
         벌크 연산 시 영속성 컨텍스트를 건너뛰고 DB에 바로 쿼리 (정합성 문제)
         이후에 이루어지는 조회 작업에 영향을 끼칠 수 있으므로 영속성 컨텍스트 초기화 필요
         -> em.flush(), em.clear() 호출
         또는 Spring Data JPA의 @Modifying(clearAutomatically = true) 사용
         */
        long count = queryFactory
                .update(member)
                .set(member.username, "adult")
                .where(member.age.goe(20))
                .execute();

        assertThat(count).isEqualTo(3);
    }

    @Test
    void bulk_delete() {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(25))
                .execute();

        assertThat(count).isEqualTo(2);
    }
}
