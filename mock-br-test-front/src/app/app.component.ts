import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

export interface UserSession {
  authenticated: boolean;
  cpf?: string;
  name?: string;
  social_name?: string;
  given_name?: string;
  family_name?: string;
  email?: string;
  phone_number?: string;
  picture?: string;
  birthdate?: string;
  nivel?: string;
  reliability_info?: {
    level: string;
    reliabilities: { id: string; updatedAt: string }[];
  };
  reliabilities?: string[];
  amr?: string[];
  mfa_verified?: boolean;
  auth_time?: number;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  loading = true;
  user: UserSession | null = null;
  status: 'loading' | 'success' | 'error' | 'unauthenticated' = 'loading';
  errorMessage = '';

  private readonly API_BASE = 'http://localhost:8080';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    const urlParams = new URLSearchParams(window.location.search);
    const queryStatus = urlParams.get('status');
    const queryMsg = urlParams.get('msg');

    if (queryStatus === 'error') {
      this.status = 'error';
      this.errorMessage = queryMsg || 'Falha na autenticação via Gov.BR. Tente novamente.';
      this.loading = false;
      return;
    }

    this.checkSession();
  }

  checkSession(): void {
    this.loading = true;
    this.http.get<UserSession>(`${this.API_BASE}/api/auth/me`, { withCredentials: true }).subscribe({
      next: (data) => {
        this.loading = false;
        if (data && data.authenticated) {
          this.user = data;
          this.status = 'success';
        } else {
          this.user = null;
          this.status = 'unauthenticated';
        }
      },
      error: () => {
        this.loading = false;
        this.status = 'unauthenticated';
      }
    });
  }

  login(): void {
    window.location.href = `${this.API_BASE}/oauth2/authorization/govbr`;
  }

  logout(): void {
    this.http.post(`${this.API_BASE}/api/auth/logout`, {}, { withCredentials: true }).subscribe({
      next: () => {
        this.user = null;
        this.status = 'unauthenticated';
      },
      error: () => {
        this.user = null;
        this.status = 'unauthenticated';
      }
    });
  }

  formatCpf(cpf?: string): string {
    if (!cpf) return '';
    const clean = cpf.replace(/\D/g, '');
    if (clean.length === 11) {
      return clean.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
    }
    return cpf;
  }
}
