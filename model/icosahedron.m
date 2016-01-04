1;

X = 0.525731112119133606;
Z = 0.850650808352039932;

vdata= [ -X 0.0   Z ;
          X 0.0   Z ;
         -X 0.0  -Z ;
          X 0.0  -Z ;
        0.0   Z   X ;
        0.0   Z  -X ;
        0.0  -Z   X ;
        0.0  -Z  -X ;
          Z   X 0.0 ;
         -Z   X 0.0 ;
          Z  -X 0.0 ;
         -Z  -X 0.0
]';

tindices = [
   3 0 4
   3 4 6 ;
   1 2 5 ;
   1 2 7 ;
   7 0 11 ;
#   7 8 0 ;
#   5 9 6 ;
#   5 6 10 ;
#   1 11 9 ;
#   2 7 10 ;
#   5 1 9 ;
#   5 2 10 ;
#   11 7 2 ;
#   5 8 3 ;
#   7 10 3 ;
#   5 2 3 ;
#   7 2 3 ;
#   3 8 10;
#   2 9 11;
#   1 8 10;
#   0 9 11
]';

n = size(tindices)(2);

for i = 1:n
  a = vdata(:,tindices(2,i)+1) - vdata(:,tindices(1,i)+1);
  b = vdata(:,tindices(3,i)+1) - vdata(:,tindices(2,i)+1);
  c = cross(a,b);
  c = c/sqrt(c'*c);
  d = (vdata(:,tindices(1,i)+1) + vdata(:,tindices(2,i)+1) + vdata(:,tindices(3,i)+1))/3;
#  display(c);
#  display(d);
  printf("Face %d orientation %d %d %d\n", i, c' * vdata(:,tindices(1,i)+1), c' * vdata(:,tindices(2,i)+1), c' * vdata(:,tindices(3,i)+1));
endfor

close all;

scatter3(vdata(1,:), vdata(2,:), vdata(3,:));
patch('Faces',tindices','Vertices',vdata','FaceColor','r');
xlabel("x");
ylabel("y");
zlabel("z");

view(00,90);
