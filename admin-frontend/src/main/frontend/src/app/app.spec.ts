import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows the version of the service once authenticated', () => {
    // Arrange
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).markVerified();
    const fixture = TestBed.createComponent(App);

    // Act
    fixture.detectChanges();
    http.match((req) => req.url.endsWith('/admin/repository/register')).forEach((req) => req.flush([]));
    http.expectOne((req) => req.url.endsWith('/admin/version'))
      .flush({ version: '11.0.4', commit: 'c0ffee1234567890', imageTag: '11.0.4-1' });
    fixture.detectChanges();

    // Assert
    const text = (fixture.nativeElement as HTMLElement).querySelector('.version')?.textContent ?? '';
    expect(text).toContain('11.0.4');
    expect(text).toContain('c0ffee12');
    expect(text).not.toContain('c0ffee1234567890');
    expect(text).toContain('11.0.4-1');
  });
});
