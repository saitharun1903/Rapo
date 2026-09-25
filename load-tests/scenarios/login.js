// Login under load: every VU signs in again and again as its own passenger. Each login verifies a BCrypt
// hash at the production cost (12), so this measures the CPU price of authentication.
import { check } from 'k6';
import { DURATION, MEASURED, SETUP_OPTIONS, VUS, createAccounts, login, reportThresholds, summaryFile } from './lib.js';

export const options = {
  ...SETUP_OPTIONS,
  scenarios: { [MEASURED]: { executor: 'constant-vus', vus: VUS, duration: DURATION } },
  thresholds: reportThresholds(['login']),
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  return { accounts: createAccounts(VUS, 'PASSENGER', 'login') };
}

export default function (data) {
  const account = data.accounts[(__VU - 1) % data.accounts.length];
  check(login(account.email, 'login'), { 'login 200': (r) => r.status === 200 });
}

export function handleSummary(summary) {
  return summaryFile('login', summary);
}
