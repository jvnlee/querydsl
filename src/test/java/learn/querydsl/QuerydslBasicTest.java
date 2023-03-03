package learn.querydsl;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import learn.querydsl.entity.Member;
import learn.querydsl.entity.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import java.util.List;

import static learn.querydsl.entity.QMember.member;
import static learn.querydsl.entity.QTeam.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

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

}
