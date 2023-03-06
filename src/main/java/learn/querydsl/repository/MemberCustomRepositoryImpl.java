package learn.querydsl.repository;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import learn.querydsl.dto.MemberSearchCond;
import learn.querydsl.dto.MemberTeamDto;
import learn.querydsl.dto.QMemberTeamDto;
import learn.querydsl.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

import static learn.querydsl.entity.QMember.member;
import static learn.querydsl.entity.QTeam.team;

@RequiredArgsConstructor
public class MemberCustomRepositoryImpl implements MemberCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<MemberTeamDto> search(MemberSearchCond cond) {
        return queryFactory
                .select(
                        new QMemberTeamDto(
                                member.id.as("memberId"),
                                member.username,
                                member.age,
                                team.id.as("teamId"),
                                team.name.as("teamName")
                        )
                )
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(cond.getUsername()),
                        teamNameEq(cond.getTeamName()),
                        ageGoe(cond.getAgeGoe()),
                        ageLoe(cond.getAgeLoe())
                )
                .fetch();
    }

    @Override
    public Page<MemberTeamDto> searchWithPaging(MemberSearchCond cond, Pageable pageable) {
        // 데이터 조회 쿼리 (페이징 적용)
        List<MemberTeamDto> content = queryFactory
                .select(
                        new QMemberTeamDto(
                                member.id.as("memberId"),
                                member.username,
                                member.age,
                                team.id.as("teamId"),
                                team.name.as("teamName")
                        )
                )
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(cond.getUsername()),
                        teamNameEq(cond.getTeamName()),
                        ageGoe(cond.getAgeGoe()),
                        ageLoe(cond.getAgeLoe())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // count 쿼리 (조건에 부합하는 로우의 총 개수를 얻는 것이기 때문에 페이징 미적용)
        JPAQuery<Long> fetchQuery = queryFactory
                .select(member.count()) // SQL 상으로는 count(member.id)와 동일
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(cond.getUsername()),
                        teamNameEq(cond.getTeamName()),
                        ageGoe(cond.getAgeGoe()),
                        ageLoe(cond.getAgeLoe())
                );

        /*
         3번째 파라미터: () -> fetchQuery.fetchOne()
         count 쿼리를 콜백으로 감싸서 필요한 경우에만 호출하도록 함 (lazy 호출)

         count 쿼리가 필요하지 않은 경우:
         1. 첫 페이지인데, 컨텐츠 사이즈가 페이지 사이즈보다 작을 때 (컨텐츠 사이즈가 곧 total 이므로 따로 구할 필요 없음)
         2. 마지막 페이지일 때 ("offset + 컨텐츠 사이즈"가 곧 total 이므로 따로 구할 필요 없음)
         */
        return PageableExecutionUtils.getPage(content, pageable, fetchQuery::fetchOne);
    }

    private BooleanExpression usernameEq(String username) {
        return username != null ? member.username.eq(username) : null;
    }

    private BooleanExpression teamNameEq(String teamName) {
        return teamName != null ? team.name.eq(teamName) : null;
    }

    private Predicate ageGoe(Integer ageGoe) {
        return ageGoe != null ? member.age.goe(ageGoe) : null;
    }

    private Predicate ageLoe(Integer ageLoe) {
        return ageLoe != null ? member.age.loe(ageLoe) : null;
    }

}
