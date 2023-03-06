package learn.querydsl.repository;

import learn.querydsl.dto.MemberSearchCond;
import learn.querydsl.dto.MemberTeamDto;
import learn.querydsl.entity.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
}