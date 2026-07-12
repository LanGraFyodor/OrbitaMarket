package ru.orbitamarket.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final PaymentRepository payments = mock(PaymentRepository.class);
  private final OutboxRepository outbox = mock(OutboxRepository.class);
  private final InboxRepository inbox = mock(InboxRepository.class);
  private final PaymentService service =
      new PaymentService(
          accounts, payments, outbox, inbox, new ObjectMapper().findAndRegisterModules());

  @Test
  void createsAccountWhenItDoesNotExist() {
    when(accounts.findByUserId("u1")).thenReturn(Optional.empty());
    when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AccountCreation response = service.createAccount("u1");

    assertThat(response.created()).isTrue();
    assertThat(response.account().userId()).isEqualTo("u1");
    assertThat(response.account().balance()).isZero();
    verify(accounts).save(any(Account.class));
  }

  @Test
  void returnsExistingAccountWithoutCreatingDuplicate() {
    when(accounts.findByUserId("u1")).thenReturn(Optional.of(new Account("u1")));

    AccountCreation response = service.createAccount("u1");

    assertThat(response.created()).isFalse();
  }

  @Test
  void rejectsMissingIdentityAndNonPositiveAmount() {
    assertThatThrownBy(() -> service.balance(" ")).isInstanceOf(PaymentApiException.class);
    assertThatThrownBy(() -> service.topUp("u1", 0)).isInstanceOf(PaymentApiException.class);
  }
}
