package ru.mirea.avia.filter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.avia.domain.UserAccount;
import ru.mirea.avia.error.BadRequestException;

import java.util.ArrayList;
import java.util.List;

/** Условия поиска пользователей через Criteria API: все значения уходят в запрос параметрами. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<UserAccount> from(UserFilter filter) {
        if (filter.q() != null && filter.q().length() > UserFilter.MAX_QUERY_LENGTH) {
            throw new BadRequestException("INVALID_QUERY", "Строка поиска длиннее " + UserFilter.MAX_QUERY_LENGTH + " символов");
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // каждое слово должно встретиться в логине или в имени: «иван admin» найдёт Ивана с логином admin@…
            for (String word : LikePatterns.words(filter.q())) {
                String like = LikePatterns.contains(word);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("login")), like, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("displayName")), like, LikePatterns.ESCAPE)));
            }
            if (filter.role() != null) {
                predicates.add(cb.isMember(filter.role(), root.get("roles")));
            }
            if (filter.enabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), filter.enabled()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
