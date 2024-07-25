-module(vec3d).
-export([
  new/3, zero/0, add/2, subtract/2, multiply/2, length/1,
  dot_product/2, cross_product/2, normalize/1, distance_to/2,
  unpack_rgb/1, of_center/1, of_bottom_center/1, of_center_with_offset/2,
  relativize/2, is_in_range/3, squared_distance_to/2, horizontal_length/1,
  horizontal_length_squared/1, equals/2, hash_code/1, to_string/1, lerp/3,
  rotate_x/2, rotate_y/2, rotate_z/2, from_polar/2, floor_along_axes/2,
  get_component_along_axis/2, with_axis/3, offset/3, negate/1
]).

-record(vec3d, {x, y, z}).

%% Create a new vector
new(X, Y, Z) ->
  #vec3d{x = X, y = Y, z = Z}.

%% Zero vector
zero() ->
  #vec3d{x = 0.0, y = 0.0, z = 0.0}.

%% Add two vectors
add(#vec3d{x = X1, y = Y1, z = Z1}, #vec3d{x = X2, y = Y2, z = Z2}) ->
  #vec3d{x = X1 + X2, y = Y1 + Y2, z = Z1 + Z2}.

%% Subtract two vectors
subtract(#vec3d{x = X1, y = Y1, z = Z1}, #vec3d{x = X2, y = Y2, z = Z2}) ->
  #vec3d{x = X1 - X2, y = Y1 - Y2, z = Z1 - Z2}.

%% Multiply vector by scalar
multiply(#vec3d{x = X, y = Y, z = Z}, Scalar) ->
  #vec3d{x = X * Scalar, y = Y * Scalar, z = Z * Scalar}.

%% Calculate length of the vector
length(#vec3d{x = X, y = Y, z = Z}) ->
  math:sqrt(X * X + Y * Y + Z * Z).

%% Dot product
dot_product(#vec3d{x = X1, y = Y1, z = Z1}, #vec3d{x = X2, y = Y2, z = Z2}) ->
  X1 * X2 + Y1 * Y2 + Z1 * Z2.

%% Cross product
cross_product(#vec3d{x = X1, y = Y1, z = Z1}, #vec3d{x = X2, y = Y2, z = Z2}) ->
  #vec3d{x = Y1 * Z2 - Z1 * Y2, y = Z1 * X2 - X1 * Z2, z = X1 * Y2 - Y1 * X2}.

%% Normalize vector
normalize(V) ->
  Len = vec3d:length(V),
  case Len < 1.0e-4 of
    true -> zero();
    false -> multiply(V, 1.0 / Len)
  end.

%% Distance between vectors
distance_to(V1, V2) ->
  vec3d:length(subtract(V1, V2)).

%% Center and Bottom Center
of_center(Vec) ->
  add(Vec, new(0.5, 0.5, 0.5)).

of_bottom_center(Vec) ->
  add(Vec, new(0.5, 0.0, 0.5)).

of_center_with_offset(Vec, DeltaY) ->
  add(Vec, new(0.5, DeltaY, 0.5)).

%% Relativize
relativize(#vec3d{x = X1, y = Y1, z = Z1}, #vec3d{x = X2, y = Y2, z = Z2}) ->
  #vec3d{x = X2 - X1, y = Y2 - Y1, z = Z2 - Z1}.

%% Is in range
is_in_range(#vec3d{x = X1, y = Y1, z = Z1}, #vec3d{x = X2, y = Y2, z = Z2}, Radius) ->
  squared_distance_to(#vec3d{x = X1, y = Y1, z = Z1}, #vec3d{x = X2, y = Y2, z = Z2}) < Radius * Radius.

%% Squared distance to
squared_distance_to(#vec3d{x = X1, y = Y1, z = Z1}, #vec3d{x = X2, y = Y2, z = Z2}) ->
  D1 = X2 - X1,
  D2 = Y2 - Y1,
  D3 = Z2 - Z1,
  D1 * D1 + D2 * D2 + D3 * D3.

%% Horizontal length
horizontal_length(#vec3d{x = X, y = _, z = Z}) ->
  math:sqrt(X * X + Z * Z).

%% Horizontal length squared
horizontal_length_squared(#vec3d{x = X, y = _, z = Z}) ->
  X * X + Z * Z.

%% Equals
equals(#vec3d{x = X1, y = Y1, z = Z1}, #vec3d{x = X2, y = Y2, z = Z2}) ->
  X1 =:= X2 andalso Y1 =:= Y2 andalso Z1 =:= Z2.

%% Hash code
hash_code(#vec3d{x = X, y = Y, z = Z}) ->
  hash = erlang:phash2(X) bxor erlang:phash2(Y) bxor erlang:phash2(Z),
  hash.

%% To string
to_string(#vec3d{x = X, y = Y, z = Z}) ->
  io_lib:format("~p, ~p, ~p", [X, Y, Z]).

%% Linear interpolation
lerp(#vec3d{x = X1, y = Y1, z = Z1}, #vec3d{x = X2, y = Y2, z = Z2}, Delta) ->
  #vec3d{
    x = X1 + (X2 - X1) * Delta,
    y = Y1 + (Y2 - Y1) * Delta,
    z = Z1 + (Z2 - Z1) * Delta
  }.

%% Rotate around X axis
rotate_x(#vec3d{x = X, y = Y, z = Z}, Angle) ->
  Cos = math:cos(Angle),
  Sin = math:sin(Angle),
  #vec3d{x = X, y = Y * Cos - Z * Sin, z = Y * Sin + Z * Cos}.

%% Rotate around Y axis
rotate_y(#vec3d{x = X, y = Y, z = Z}, Angle) ->
  Cos = math:cos(Angle),
  Sin = math:sin(Angle),
  #vec3d{x = X * Cos + Z * Sin, y = Y, z = Z * Cos - X * Sin}.

%% Rotate around Z axis
rotate_z(#vec3d{x = X, y = Y, z = Z}, Angle) ->
  Cos = math:cos(Angle),
  Sin = math:sin(Angle),
  #vec3d{x = X * Cos - Y * Sin, y = X * Sin + Y * Cos, z = Z}.

%% From polar coordinates
from_polar(Pitch, Yaw) ->
  CosYaw = math:cos(-Yaw * math:pi() / 180.0 - math:pi()),
  SinYaw = math:sin(-Yaw * math:pi() / 180.0 - math:pi()),
  CosPitch = math:cos(-Pitch * math:pi() / 180.0),
  SinPitch = math:sin(-Pitch * math:pi() / 180.0),
  #vec3d{x = SinYaw * CosPitch, y = SinPitch, z = CosYaw * CosPitch}.

%% Floor along axes
floor_along_axes(#vec3d{x = X, y = Y, z = Z}, Axes) ->
  NewX = case lists:member('x', Axes) of true -> math:floor(X); false -> X end,
  NewY = case lists:member('y', Axes) of true -> math:floor(Y); false -> Y end,
  NewZ = case lists:member('z', Axes) of true -> math:floor(Z); false -> Z end,
  #vec3d{x = NewX, y = NewY, z = NewZ}.

%% Get component along axis
get_component_along_axis(#vec3d{x = X, y = Y, z = Z}, 'x') -> X;
get_component_along_axis(#vec3d{x = X, y = Y, z = Z}, 'y') -> Y;
get_component_along_axis(#vec3d{x = X, y = Y, z = Z}, 'z') -> Z.

%% Set value along axis
with_axis(#vec3d{x = X, y = Y, z = Z}, 'x', Value) -> #vec3d{x = Value, y = Y, z = Z};
with_axis(#vec3d{x = X, y = Y, z = Z}, 'y', Value) -> #vec3d{x = X, y = Value, z = Z};
with_axis(#vec3d{x = X, y = Y, z = Z}, 'z', Value) -> #vec3d{x = X, y = Y, z = Value}.

%% Offset
offset(#vec3d{x = X, y = Y, z = Z}, #vec3d{x = DX, y = DY, z = DZ}, Value) ->
  #vec3d{x = X + Value * DX, y = Y + Value * DY, z = Z + Value * DZ}.

%% Negate vector
negate(#vec3d{x = X, y = Y, z = Z}) ->
  #vec3d{x = -X, y = -Y, z = -Z}.
