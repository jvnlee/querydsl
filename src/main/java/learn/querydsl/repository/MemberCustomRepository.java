package learn.querydsl.repository;

import learn.querydsl.dto.MemberSearchCond;
import learn.querydsl.dto.MemberTeamDto;

import java.util.List;

public interface MemberCustomRepository {

    List<MemberTeamDto> search(MemberSearchCond cond);

}
