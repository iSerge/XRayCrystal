1;

a1 = [  0    ; 0 ; 3e-10];
a2 = [  3e-10; 0 ; 0];
a3 = [ -3e-10; 0 ; 0];
%a3 = [ 0     ; -3e-10 ; 0];


lambda = 0.5e-10;

L = 12;
R = 5;

k = 2*pi/lambda;

N = 500;

x = linspace(-L/2, L/2, N);
y = linspace(-L/2, L/2, N);
[xx,yy] = meshgrid(x,y);

I1 = exp(1i*k*(a1(3)+sqrt((a1(1)-xx).^2 + (a1(2)-yy).^2 + (a1(3)-R).^2)));
I2 = exp(1i*k*(a2(3)+sqrt((a2(1)-xx).^2 + (a2(2)-yy).^2 + (a2(3)-R).^2)));
I3 = exp(1i*k*(a3(3)+sqrt((a3(1)-xx).^2 + (a3(2)-yy).^2 + (a3(3)-R).^2)));
I = I1+I2+I3;

colormap gray;
imagesc([-L/2 L/2], [-L/2 L/2], abs(I));
