$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Original TV/play geometry and an outlined rendering of the personal fork's name.
# Windows' installed Arial Bold renders this wordmark; no font file is bundled.
$assetRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $assetRoot '../..')).Path
$resourceRoot = Join-Path $repoRoot 'smarttubetv/src/stpersonal/res/mipmap-nodpi'
[IO.Directory]::CreateDirectory($resourceRoot) | Out-Null
$culture = [Globalization.CultureInfo]::InvariantCulture

function New-RoundedPath([single]$x, [single]$y, [single]$width, [single]$height, [single]$radius) {
    $path = New-Object Drawing.Drawing2D.GraphicsPath
    $diameter = 2 * $radius
    $path.AddArc($x, $y, $diameter, $diameter, 180, 90)
    $path.AddArc(($x + $width - $diameter), $y, $diameter, $diameter, 270, 90)
    $path.AddArc(($x + $width - $diameter), ($y + $height - $diameter), $diameter, $diameter, 0, 90)
    $path.AddArc($x, ($y + $height - $diameter), $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-PolygonPath([single[]]$coordinates) {
    $points = New-Object 'Collections.Generic.List[Drawing.PointF]'
    for ($index = 0; $index -lt $coordinates.Length; $index += 2) {
        $points.Add((New-Object Drawing.PointF($coordinates[$index], $coordinates[$index + 1])))
    }
    $path = New-Object Drawing.Drawing2D.GraphicsPath
    $path.AddPolygon($points.ToArray())
    return $path
}

function ConvertTo-SvgPath($path) {
    $points = $path.PathPoints
    $types = $path.PathTypes
    $commands = New-Object Text.StringBuilder
    for ($index = 0; $index -lt $points.Length; $index++) {
        $kind = $types[$index] -band 7
        if ($kind -eq 0 -or $kind -eq 1) {
            $command = if ($kind -eq 0) { 'M' } else { 'L' }
            [void]$commands.Append($command + $points[$index].X.ToString('0.###', $culture) + ',' + $points[$index].Y.ToString('0.###', $culture))
        } elseif ($kind -eq 3) {
            [void]$commands.Append('C')
            for ($part = 0; $part -lt 3; $part++) {
                if ($part -gt 0) { [void]$commands.Append(' ') }
                [void]$commands.Append($points[$index + $part].X.ToString('0.###', $culture) + ',' + $points[$index + $part].Y.ToString('0.###', $culture))
            }
            $index += 2
        }
        if (($types[$index] -band 128) -ne 0) { [void]$commands.Append('Z') }
    }
    return $commands.ToString()
}

function Add-Shape($shapes, $path, [string]$color, [single]$opacity = 1) {
    $shapes.Add([pscustomobject]@{ Path = $path; Color = $color; Opacity = $opacity })
}

function Add-Tv($shapes, [single]$x, [single]$y, [single]$size, [string]$frame, [string]$play, [single]$opacity = 1) {
    $ring = New-RoundedPath 48 72 224 152 24
    $inside = New-RoundedPath 60 84 200 128 14
    $ring.AddPath($inside, $false)
    $inside.Dispose()
    $antenna = New-PolygonPath @(118,38,160,72,202,38,211,48,166,84,154,84,109,48)
    $stem = New-RoundedPath 148 220 24 31 4
    $foot = New-RoundedPath 104 247 112 12 6
    $triangle = New-PolygonPath @(130,111,130,185,198,148)
    $matrix = New-Object Drawing.Drawing2D.Matrix(($size / 320), 0, 0, ($size / 320), $x, $y)
    foreach ($path in @($antenna, $ring, $stem, $foot)) { $path.Transform($matrix); Add-Shape $shapes $path $frame $opacity }
    $triangle.Transform($matrix)
    Add-Shape $shapes $triangle $play $opacity
    $matrix.Dispose()
}

function Add-Wordmark($shapes, [string]$color, [single]$opacity = 1) {
    $path = New-Object Drawing.Drawing2D.GraphicsPath
    $font = New-Object Drawing.FontFamily('Arial')
    $path.AddString('SmartTubeY', $font, [int][Drawing.FontStyle]::Bold, 29, (New-Object Drawing.PointF(0,0)), [Drawing.StringFormat]::GenericTypographic)
    $bounds = $path.GetBounds()
    $matrix = New-Object Drawing.Drawing2D.Matrix
    $matrix.Translate((110 - $bounds.X), (90 - $bounds.Height / 2 - $bounds.Y))
    $path.Transform($matrix)
    Add-Shape $shapes $path $color $opacity
    $font.Dispose(); $matrix.Dispose()
}

function Save-Asset([string]$name, [int]$width, [int]$height, $shapes) {
    # Both formats use exactly the same vector paths. Raster mipmaps work with TV launchers
    # that decode banner/icon resources as bitmaps instead of inflating vector drawables.
    $svg = New-Object Text.StringBuilder
    [void]$svg.AppendLine('<svg xmlns="http://www.w3.org/2000/svg" width="' + $width + '" height="' + $height + '" viewBox="0 0 ' + $width + ' ' + $height + '" role="img" aria-label="SmartTubeY">')
    $scale = 4
    $large = New-Object Drawing.Bitmap(($width * $scale), ($height * $scale), [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($large)
    $graphics.Clear([Drawing.Color]::Transparent)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.ScaleTransform($scale, $scale)
    foreach ($shape in $shapes) {
        [void]$svg.AppendLine('  <path fill="' + $shape.Color + '" fill-opacity="' + $shape.Opacity.ToString('0.###', $culture) + '" fill-rule="evenodd" d="' + (ConvertTo-SvgPath $shape.Path) + '"/>')
        $color = [Drawing.ColorTranslator]::FromHtml($shape.Color)
        $brush = New-Object Drawing.SolidBrush([Drawing.Color]::FromArgb([int](255 * $shape.Opacity), $color))
        $graphics.FillPath($brush, $shape.Path)
        $brush.Dispose(); $shape.Path.Dispose()
    }
    [void]$svg.AppendLine('</svg>')
    [IO.File]::WriteAllText((Join-Path $assetRoot ($name + '.svg')), $svg.ToString(), (New-Object Text.UTF8Encoding($false)))
    $graphics.Dispose()
    $bitmap = New-Object Drawing.Bitmap($width, $height, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    if ($name -eq 'app_banner') { $graphics.Clear([Drawing.ColorTranslator]::FromHtml('#151518')) }
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($large, 0, 0, $width, $height)
    $bitmap.Save((Join-Path $resourceRoot ($name + '.png')), [Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $bitmap.Dispose(); $large.Dispose()
}

foreach ($name in @('app_icon', 'app_icon_alt')) {
    $shapes = New-Object 'Collections.Generic.List[object]'
    $alternate = $name -eq 'app_icon_alt'
    Add-Shape $shapes (New-RoundedPath 8 8 304 304 56) $(if ($alternate) { '#D92A35' } else { '#151518' })
    Add-Tv $shapes 0 0 320 $(if ($alternate) { '#FFFFFF' } else { '#EF3340' }) '#FFFFFF'
    Save-Asset $name 320 320 $shapes
}

foreach ($name in @('app_banner', 'app_logo', 'app_logo_semi_red', 'app_logo_semi_grey')) {
    $shapes = New-Object 'Collections.Generic.List[object]'
    if ($name -eq 'app_banner') { Add-Shape $shapes (New-PolygonPath @(0,0,320,0,320,180,0,180)) '#151518' }
    $opacity = if ($name -like '*semi*') { 0.65 } else { 1 }
    $frame = if ($name -eq 'app_logo_semi_grey') { '#D6D6D6' } else { '#EF3340' }
    Add-Tv $shapes 8 45 100 $frame '#FFFFFF' $opacity
    Add-Wordmark $shapes '#FFFFFF' $opacity
    Save-Asset $name 320 180 $shapes
}
