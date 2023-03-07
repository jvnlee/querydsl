package learn.querydsl.repository;

import learn.querydsl.dto.MemberSearchCond;
import learn.querydsl.dto.MemberTeamDto;
import learn.querydsl.entity.Member;
import learn.querydsl.entity.QMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import java.util.List;

import static learn.querydsl.entity.QMember.member;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MemberRepositoryTest {

    @Autowired
    EntityManager em;

    @Autowired
    MemberRepository memberRepository;

    @Test
    void search() {
        Member member1 = new Member("member1", 10);
        Member member2 = new Member("member2", 20);
        Member member3 = new Member("member3", 30);
        Member member4 = new Member("member4", 40);

        memberRepository.save(member1);
        memberRepository.save(member2);
        memberRepository.save(member3);
        memberRepository.save(member4);

        MemberSearchCond cond = new MemberSearchCond();
        cond.setAgeGoe(25);
        cond.setAgeLoe(35);

        List<MemberTeamDto> result = memberRepository.search(cond);

        assertThat(result).extracting("username").containsExactly("member3");
    }

    @Test
    void searchWithPaging() {
        Member member1 = new Member("member1", 10);
        Member member2 = new Member("member2", 20);
        Member member3 = new Member("member3", 30);
        Member member4 = new Member("member4", 40);

        memberRepository.save(member1);
        memberRepository.save(member2);
        memberRepository.save(member3);
        memberRepository.save(member4);

        MemberSearchCond cond = new MemberSearchCond();
        cond.setAgeGoe(25);
        cond.setAgeLoe(35);
        PageRequest pageRequest = PageRequest.of(0, 3); // 0번 페이지, 크기 3

        /*
         조건에 부합하는 컨텐츠는 1개 (member3)
         요청하는 페이지는 0번, 페이지 크기는 3

         첫 페이지이면서 컨텐츠의 크기가 페이지의 크기보다 작으므로 따로 카운트 쿼리를 하지 않아도 total count가 1인 것을 알 수 있음
         따라서 PageableExecutionUtils.getPage()는 카운트 쿼리를 날리지 않음
         (쿼리 로그를 통해 count 쿼리가 나가지 않은 것을 확인할 수 있음)
         */
        Page<MemberTeamDto> result = memberRepository.searchWithPaging(cond, pageRequest);

        assertThat(result.getContent().size()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting("username")
                .containsExactly("member3");
    }

    @Test
    void predicateExecutor() {
        Member member1 = new Member("member1", 10);
        Member member2 = new Member("member2", 20);
        Member member3 = new Member("member3", 30);
        Member member4 = new Member("member4", 40);

        memberRepository.save(member1);
        memberRepository.save(member2);
        memberRepository.save(member3);
        memberRepository.save(member4);

        /*
        조회 메서드에 간편하게 조건절을 추가할 수 있어 편리해보이는 기능이지만 실무에 적용하기는 다소 부적합함

        한계점:
        1. 외부 조인 불가능 (묵시적 조인 즉 inner join은 가능)
        - RDB를 사용하는데 조인을 할 수 없다는 것은 너무 큰 제약 (한 테이블만 가지고 할 수 있는 작업은 얼마 없음)
        2. repository를 호출하는 클라이언트가 QueryDSL이라는 특정 기술에 의존하게 됨
        - 데이터 접근 계층의 기술이 캡슐화되지 않고 다른 계층에서 쓰이는 것은 구조상 좋지 않음
         */
        Iterable<Member> result = memberRepository
                .findAll(
                        member.age.between(20, 40).and(member.username.eq("member3")) // 파라미터로 Predicate을 넘김
                );

        assertThat(result).extracting("username").containsExactly("member3");
    }
}