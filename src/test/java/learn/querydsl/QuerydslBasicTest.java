package learn.querydsl;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
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

}
