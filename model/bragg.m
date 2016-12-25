1;

lambda = 1.5e-10;

L = 15;
R = 5;

a = 5e-10;

A = [a 0 0];
B = [0 a 0];
C = [0 0 a];

N = 500;

iR = [0 0 1];

x = linspace(-L/2, L/2, N);
y = linspace(-L/2, L/2, N);

I = zeros(N,N);

for m = 1:N
    for n = 1:N
        v = [x(n), y(m), R];
        v = v/norm(v);
        
        p = (v - iR)/2;
        p = p/norm(p);

        h = round(p(1)*10);
        k = round(p(2)*10);
        l = round(p(3)*10);

        [h,k,l] = normIndices(h,k,l);
        
        d = Dhkl(a,h,k,l);
        
        theta = acos(iR*v')/2;
        
        nn = 2*d*sin(theta)/lambda;
        
        nn = abs(nn-round(nn));

        if(nn < 0.01)
            I(m,n) = 1 - nn*100;
        else
            I(m,n) = 0;
        end
    end
end

colormap gray;
imagesc([-L/2 L/2], [-L/2 L/2], I);
