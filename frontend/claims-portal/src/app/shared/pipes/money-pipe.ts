import { Pipe, PipeTransform } from '@angular/core';

import { formatMoney } from '@shared/utils/money';

/** `{{ claim.claimAmount | money: claim.currency }}` gives `SGD 2,500.00`; add `: true` for whole units. */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number | null | undefined, currency: string | null | undefined, whole = false): string {
    return formatMoney(amount, currency, whole);
  }
}
