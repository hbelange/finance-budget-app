import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from '@auth0/auth0-angular';
import { filter, switchMap, take, tap, map } from 'rxjs';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isLoading$.pipe(
    filter(isLoading => !isLoading),
    take(1),
    switchMap(() => auth.isAuthenticated$.pipe(take(1))),
    tap(isAuthenticated => {
      if (!isAuthenticated) {
        auth.loginWithRedirect({ appState: { target: state.url } });
      }
    }),
    map(isAuthenticated => isAuthenticated)
  );
};
