import {
  useLayoutEffect,
  useRef,
  useState,
} from "react";

interface ElementSize {
  width: number;
  height: number;
}

export function useElementSize<
  T extends HTMLElement,
>() {
  const ref = useRef<T | null>(null);

  const [size, setSize] =
    useState<ElementSize>({
      width: 0,
      height: 0,
    });

  useLayoutEffect(() => {
    const element = ref.current;

    if (!element) {
      return;
    }

    const actualizar = () => {
      const rect =
        element.getBoundingClientRect();

      setSize({
        width: Math.max(
          0,
          Math.round(rect.width),
        ),
        height: Math.max(
          0,
          Math.round(rect.height),
        ),
      });
    };

    actualizar();

    const observer =
      new ResizeObserver(actualizar);

    observer.observe(element);

    return () => {
      observer.disconnect();
    };
  }, []);

  return {
    ref,
    ...size,
  };
}
