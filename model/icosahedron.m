1;

X = 0.525731112119133606;
Z = 0.850650808352039932;

vdata= [ -X 0.0   Z ; % 1
          X 0.0   Z ; % 2
         -X 0.0  -Z ; % 3
          X 0.0  -Z ; % 4
        0.0   Z   X ; % 5
        0.0   Z  -X ; % 6
        0.0  -Z   X ; % 7
        0.0  -Z  -X ; % 8
          Z   X 0.0 ; % 9
         -Z   X 0.0 ; % 10
          Z  -X 0.0 ; % 11
         -Z  -X 0.0   % 12
]';

tindices = [
    8  4 11 ; % 1
   11  4  9 ; % 2
    1  2  5 ; % 3
    9  2 11 ; % 4
   11  7  8 ; % 5
    7 12  8 ; % 6
    8 12  3 ; % 7
    3  6  4 ; % 8
    4  6  9 ; % 9
   11  2  7 ; % 10
    6  5  9 ; % 11
    5  2  9 ; % 12
   1  10 12 ; % 13
   12  7  1 ; % 14
    2  1  7 ; % 15
    5 10  1 ; % 16
   10  6  3 ; % 17
    3 12 10 ; % 18
    8  3  4 ; % 19
    5  6 10   % 20
]';

n = size(tindices,2);

for i = 1:n
  a = vdata(:,tindices(2,i)) - vdata(:,tindices(1,i));
  b = vdata(:,tindices(3,i)) - vdata(:,tindices(2,i));
  c = cross(a,b);
  c = c/sqrt(c'*c);
  d = (vdata(:,tindices(1,i)) + vdata(:,tindices(2,i)) + vdata(:,tindices(3,i)))/3;
%  display(c);
%  display(d);
%  printf('Face %d orientation %d %d %d\n', i, c' * vdata(:,tindices(1,i)), c' * vdata(:,tindices(2,i)), c' * vdata(:,tindices(3,i)));
end

close all;

%scatter3(vdata(1,:), vdata(2,:), vdata(3,:));
patch('Faces',tindices','Vertices',vdata','FaceColor','none');
xlabel('x');
ylabel('y');
zlabel('z');

view(00,90);
