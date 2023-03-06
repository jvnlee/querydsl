package learn.querydsl.repository;

import learn.querydsl.dto.MemberSearchCond;
import learn.querydsl.dto.MemberTeamDto;
import learn.querydsl.entity.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import java.util.List;

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
}