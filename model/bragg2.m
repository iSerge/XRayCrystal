1;

lim = 10;

lMin = 0.5e-10; % 0.5 ?
lMax = 5.0e-10; % 5.0 ?

L = 15;
R = 5;

a = 5e-10;

N = 500;

iR = [0 0 1];

I = zeros(N,N);

for h = -lim:lim
    for k = -lim:lim
        for l = -lim:lim
            [H,K,LL] = normIndices(h,k,l);
            if (H ~= h || K ~= k || LL ~= l || 0 == l)
                continue
            end
            
            d = Dhkl(a, h, k ,l);
            
            n = [h k l];
            n = n/norm(n);
            
            r = iR - 2 * (iR * n') * n;
            theta = (pi - acos(iR*v'))/2;
            
            %n*lambda = 2*d*sin(theta)
            Lam = 2*d*sin(theta) ./ [1 2 3 4 5 6 7 8 9];
            
            if ( Lam( lMin < Lam & Lam < lMax))
                x = round((r(1) * R/r(3) + L/2)*N/L);
                y = round((r(2) * R/r(3) + L/2)*N/L);
                
                for X = (x-5):(x+5)
                    for Y = (y-5):(y+5)
                        if(1 <= X && X < N && 1<= Y && Y < N)
                            dx = X-x;
                            dy = Y-y;
                            
                            s = sqrt(dx*dx+dy*dy);
                            if(s < 5)
                                I(X,Y) = 5 - s;
                            end
                        end
                    end
                end
            end
        end
    end
end

colormap gray;
imagesc([-L/2 L/2], [-L/2 L/2], I);
