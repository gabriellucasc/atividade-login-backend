import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  imports: [FormsModule],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class Login {
  email: string = '';
  senha: string = '';

  onEntrar() {
    console.log('E-mail:', this.email);
    console.log('Senha:', this.senha);
  }
}
