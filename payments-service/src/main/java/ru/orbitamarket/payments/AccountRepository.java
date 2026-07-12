package ru.orbitamarket.payments;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccountRepository extends JpaRepository<Account, UUID> {

  Optional<Account> findByUserId(String userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
            update Account account
               set account.balance = account.balance - :amount,
                   account.version = account.version + 1
             where account.userId = :userId
               and account.balance >= :amount
            """)
  int debitIfEnough(@Param("userId") String userId, @Param("amount") long amount);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
            update Account account
               set account.balance = account.balance + :amount,
                   account.version = account.version + 1
             where account.userId = :userId
            """)
  int credit(@Param("userId") String userId, @Param("amount") long amount);
}
