function [H K L] = normIndices(h, k ,l)
    if(0 == h && 0 == k)
        H = 0; K = 0; L = 1;
    elseif(0 == k && 0 == l)
        H = 1; K = 0; L = 0;
    elseif(0 == h && 0 == l)
        H = 0; K = 1; L = 0;
    elseif(0 == h)
        d = gcd(k,l);
        H = 0; K = k/d; L = l/d;
    elseif(0 == k)
        d = gcd(h,l);
        H = h/d; K = 0; L = l/d;
    elseif(0 == l)
        d = gcd(h,k);
        H = h/d; K = k/d; L = 0;
    else
        d = gcd(gcd(k,l),h);
        H = h/d; K = k/d; L = l/d;
    end        
end